package com.nuvio.app.features.plugins.runtime.cs3

import co.touchlab.kermit.Logger
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.nuvio.app.features.plugins.PluginStorage
import com.nuvio.app.features.plugins.pluginDigestHex
import dalvik.system.DexClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

object CloudstreamPluginLoader {
    private val log = Logger.withTag("CS3PluginLoader")
    private val loadedApis = ConcurrentHashMap<String, MainAPI>()
    private var pluginCacheDir: File? = null

    /**
     * Dex loading (DexClassLoader + dex2oat) is extremely heavy. The streams
     * screen fans out one job per provider, so without a bound every enabled
     * .cs3 would be dex-loaded at the same time: CPU pegged, dexopt
     * contention on the shared opt dir, and OOM risk on low-end devices.
     */
    private val dexLoadPermits = Semaphore(permits = 2)

    /**
     * Single-flight guards per scraper. Concurrent stream jobs for the same
     * provider must not write and dex-load the same plugin file twice (the
     * old check-then-act on [loadedApis] raced and concurrent writes could
     * tear the plugin file).
     */
    private val scraperLoadMutexes = ConcurrentHashMap<String, Mutex>()

    fun init(cacheDir: File) {
        pluginCacheDir = File(cacheDir, "cs3_plugins").apply { mkdirs() }
    }

    suspend fun loadApi(scraperId: String, cs3Data: ByteArray): MainAPI? = withContext(Dispatchers.IO) {
        val fastPath = loadedApis[scraperId]
        if (fastPath != null || cs3Data.isEmpty()) {
            fastPath
        } else {
            val mutex = scraperLoadMutexes.getOrPut(scraperId) { Mutex() }
            try {
                mutex.withLock {
                    loadedApis[scraperId] ?: dexLoadPermits.withPermit {
                        loadApiLocked(scraperId, cs3Data)
                    }
                }
            } finally {
                if (!mutex.isLocked) scraperLoadMutexes.remove(scraperId, mutex)
            }
        }
    }

    private fun loadApiLocked(scraperId: String, cs3Data: ByteArray): MainAPI? {
        try {
            val cacheDir = pluginCacheDir ?: File(System.getProperty("java.io.tmpdir") ?: "/tmp", "cs3_plugins").apply { mkdirs() }
            val pluginFile = File(cacheDir, "${pluginDigestHex("SHA256", scraperId)}_plugin.cs3")
            writePluginBytesAtomically(pluginFile, cs3Data)

            val optDir = File(cacheDir, "opt").apply { mkdirs() }
            val parentClassLoader = CloudstreamPluginLoader::class.java.classLoader
            val dexClassLoader = DexClassLoader(
                pluginFile.absolutePath,
                optDir.absolutePath,
                null,
                parentClassLoader
            )

            var pluginClassName: String? = null

            // 1. Try reading manifest.json inside cs3 zip
            runCatching {
                ZipFile(pluginFile).use { zip ->
                    val manifestEntry = zip.getEntry("manifest.json")
                    if (manifestEntry != null) {
                        val text = zip.getInputStream(manifestEntry).bufferedReader().readText()
                        val json = JSONObject(text)
                        pluginClassName = json.optString("pluginClassName").takeIf { !it.isNullOrBlank() }
                    }
                }
            }

            var api: MainAPI? = null

            if (pluginClassName != null) {
                runCatching {
                    val cls = dexClassLoader.loadClass(pluginClassName)
                    if (BasePlugin::class.java.isAssignableFrom(cls)) {
                        val pluginInstance = cls.getDeclaredConstructor().newInstance() as BasePlugin
                        invokePluginLoad(cls, pluginInstance)
                        api = pluginInstance.registeredApis.firstOrNull()
                    } else if (MainAPI::class.java.isAssignableFrom(cls)) {
                        api = cls.getDeclaredConstructor().newInstance() as MainAPI
                    }
                }.onFailure { log.w(it) { "Failed to instantiate plugin class from manifest: $pluginClassName" } }
            }

            if (api == null) {
                // 2. Scan zip entries for plugin class names
                runCatching {
                    ZipFile(pluginFile).use { zip ->
                        val entries = zip.entries()
                        while (entries.hasMoreElements() && api == null) {
                            val entry = entries.nextElement()
                            if (entry.name.endsWith(".class")) {
                                val className = entry.name.removeSuffix(".class").replace('/', '.')
                                if (className.endsWith("Plugin") || !className.contains("$")) {
                                    runCatching {
                                        val cls = dexClassLoader.loadClass(className)
                                        if (BasePlugin::class.java.isAssignableFrom(cls) && !cls.isInterface) {
                                            val instance = cls.getDeclaredConstructor().newInstance() as BasePlugin
                                            invokePluginLoad(cls, instance)
                                            if (instance.registeredApis.isNotEmpty()) {
                                                api = instance.registeredApis.firstOrNull()
                                            }
                                        } else if (MainAPI::class.java.isAssignableFrom(cls) && !cls.isInterface) {
                                            api = cls.getDeclaredConstructor().newInstance() as MainAPI
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (api != null) {
                loadedApis[scraperId] = api
                log.i { "Successfully loaded CloudStream API: ${api.name} (${api.mainUrl}) for scraper $scraperId" }
            } else {
                log.w { "No MainAPI found in CloudStream plugin $scraperId" }
            }

            return api
        } catch (e: Throwable) {
            log.e(e) { "Failed to load CloudStream plugin $scraperId" }
            return null
        }
    }

    /**
     * Writes the plugin binary atomically (temp file + rename) so a reader
     * never observes a torn file. Skips the write when the cached bytes are
     * identical — but compares content, not just length, so a provider update
     * that happens to have the same byte size still applies.
     */
    private fun writePluginBytesAtomically(pluginFile: File, cs3Data: ByteArray) {
        if (pluginBytesUpToDate(pluginFile, cs3Data)) return
        val parent = pluginFile.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            error("Unable to create plugin cache dir: ${parent.absolutePath}")
        }
        val tmp = File(parent, "${pluginFile.name}.tmp")
        tmp.writeBytes(cs3Data)
        if (!tmp.renameTo(pluginFile)) {
            runCatching { pluginFile.writeBytes(cs3Data) }.getOrThrow()
        }
        runCatching { if (tmp.exists()) tmp.delete() }
    }

    private fun pluginBytesUpToDate(pluginFile: File, cs3Data: ByteArray): Boolean {
        if (!pluginFile.isFile || pluginFile.length() != cs3Data.size.toLong()) return false
        return runCatching { pluginFile.readBytes().contentEquals(cs3Data) }.getOrDefault(false)
    }

    private fun invokePluginLoad(cls: Class<*>, instance: BasePlugin) {
        // Only use a 1-arg load(...) overload when its parameter can actually
        // receive an Android Context. Blindly invoking any 1-arg overload (or
        // passing a null Context when ActivityThread lookup fails) would crash
        // plugins that declare load(...) with an incompatible signature.
        val loadWithContext = cls.methods.firstOrNull { method ->
            method.name == "load" &&
                method.parameterTypes.size == 1 &&
                isContextCompatible(method.parameterTypes[0])
        }
        if (loadWithContext != null) {
            val appCtx = runCatching {
                val threadCls = Class.forName("android.app.ActivityThread")
                val method = threadCls.getMethod("currentApplication")
                method.invoke(null)
            }.getOrNull()
            if (appCtx != null) {
                val invoked = runCatching { loadWithContext.invoke(instance, appCtx) }
                    .onFailure { log.w(it) { "Plugin load(Context) failed, falling back to load()" } }
                    .isSuccess
                if (invoked) return
            }
            runCatching { instance.load() }
        } else {
            instance.load()
        }
    }

    private fun isContextCompatible(paramType: Class<*>): Boolean =
        runCatching {
            Class.forName("android.content.Context").isAssignableFrom(paramType)
        }.getOrDefault(false)

    fun clearCache() {
        loadedApis.clear()
    }
}
