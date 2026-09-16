package com.nuvio.app.features.plugins.runtime.cs3

import android.content.Context
import co.touchlab.kermit.Logger
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.nuvio.app.features.plugins.pluginDigestHex
import dalvik.system.DexFile
import dalvik.system.PathClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

object CloudstreamPluginLoader {
    private val log = Logger.withTag("CS3PluginLoader")
    private val loadedApis = ConcurrentHashMap<String, MainAPI>()
    private var pluginCacheDir: File? = null
    private var appContext: Context? = null

    /**
     * Dex loading (dexopt/verify) is heavy. Bound concurrent plugin loads.
     */
    private val dexLoadPermits = Semaphore(permits = 2)

    /**
     * Single-flight guards per scraper to prevent concurrent writes.
     */
    private val scraperLoadMutexes = ConcurrentHashMap<String, Mutex>()

    fun init(cacheDir: File, context: Context? = null) {
        pluginCacheDir = File(cacheDir, "cs3_plugins").apply { mkdirs() }
        if (context != null) appContext = context.applicationContext
    }

    private fun resolveContext(): Context? =
        appContext ?: runCatching {
            val threadCls = Class.forName("android.app.ActivityThread")
            val method = threadCls.getMethod("currentApplication")
            (method.invoke(null) as? Context)?.applicationContext
        }.getOrNull()?.also {
            appContext = it
        }

    private fun resolveCacheDir(): File {
        pluginCacheDir?.let { return it }
        val ctx = resolveContext()
        val dir = if (ctx != null) {
            File(ctx.codeCacheDir ?: ctx.cacheDir, "cs3_plugins")
        } else {
            File(System.getProperty("java.io.tmpdir") ?: "/tmp", "cs3_plugins")
        }
        dir.mkdirs()
        pluginCacheDir = dir
        return dir
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
            val cacheDir = resolveCacheDir()
            val pluginFile = File(cacheDir, "${pluginDigestHex("SHA256", scraperId)}_plugin.cs3")
            writePluginBytesAtomically(pluginFile, cs3Data)

            val parentClassLoader = CloudstreamPluginLoader::class.java.classLoader
            val classLoader = PathClassLoader(pluginFile.absolutePath, parentClassLoader)

            var pluginClassName: String? = null

            // 1. Try reading manifest.json inside cs3 zip
            runCatching {
                ZipFile(pluginFile).use { zip ->
                    val manifestEntry = zip.getEntry("manifest.json")
                    if (manifestEntry != null) {
                        val text = zip.getInputStream(manifestEntry).bufferedReader().readText()
                        val json = JSONObject(text)
                        pluginClassName = json.optString("pluginClassName").takeIf { !it.isNullOrBlank() }
                            ?: json.optString("class").takeIf { !it.isNullOrBlank() }
                            ?: json.optString("pluginClass").takeIf { !it.isNullOrBlank() }
                            ?: json.optString("mainClass").takeIf { !it.isNullOrBlank() }
                    }
                }
            }

            var api: MainAPI? = null

            if (pluginClassName != null) {
                runCatching {
                    val cls = classLoader.loadClass(pluginClassName)
                    val pluginInstance = instantiate(cls, BasePlugin::class.java)
                    if (pluginInstance != null) {
                        invokePluginLoad(cls, pluginInstance)
                        api = findMatchingApi(pluginInstance.registeredApis, scraperId)
                    } else {
                        val mainApiInstance = instantiate(cls, MainAPI::class.java)
                        if (mainApiInstance != null) {
                            api = mainApiInstance
                        }
                    }
                }.onFailure { log.w(it) { "Failed to instantiate plugin class from manifest: $pluginClassName" } }
            }

            if (api == null) {
                // 2. Scan DEX entries for plugin / MainAPI class names
                runCatching {
                    @Suppress("DEPRECATION")
                    val dexFile = DexFile(pluginFile)
                    val entries = dexFile.entries()
                    while (entries.hasMoreElements() && api == null) {
                        val className = entries.nextElement()
                        if (className.endsWith("Plugin") || className.endsWith("Provider") || !className.contains("$")) {
                            runCatching {
                                val cls = classLoader.loadClass(className)
                                val pluginInstance = instantiate(cls, BasePlugin::class.java)
                                if (pluginInstance != null) {
                                    invokePluginLoad(cls, pluginInstance)
                                    if (pluginInstance.registeredApis.isNotEmpty()) {
                                        api = findMatchingApi(pluginInstance.registeredApis, scraperId)
                                    }
                                } else {
                                    val mainApiInstance = instantiate(cls, MainAPI::class.java)
                                    if (mainApiInstance != null) {
                                        api = mainApiInstance
                                    }
                                }
                            }
                        }
                    }
                }.onFailure { log.w(it) { "Failed to scan dex classes for plugin $scraperId" } }
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

    private fun <T : Any> instantiate(cls: Class<*>, expectedType: Class<T>): T? {
        if (!expectedType.isAssignableFrom(cls) || cls.isInterface || Modifier.isAbstract(cls.modifiers)) {
            return null
        }
        // 1. Try Kotlin object singleton INSTANCE field
        runCatching {
            val field = cls.getDeclaredField("INSTANCE")
            field.isAccessible = true
            val obj = field.get(null)
            if (expectedType.isInstance(obj)) {
                @Suppress("UNCHECKED_CAST")
                return obj as T
            }
        }
        // 2. Try zero-arg constructor (make accessible in case it is internal or private)
        runCatching {
            val ctor = cls.getDeclaredConstructor()
            ctor.isAccessible = true
            val obj = ctor.newInstance()
            if (expectedType.isInstance(obj)) {
                @Suppress("UNCHECKED_CAST")
                return obj as T
            }
        }
        return null
    }

    private fun findMatchingApi(apis: List<MainAPI>, scraperId: String): MainAPI? {
        if (apis.isEmpty()) return null
        if (apis.size == 1) return apis.first()
        val cleanId = scraperId.substringAfterLast(':').lowercase()
        return apis.firstOrNull { it.name.lowercase() == cleanId }
            ?: apis.firstOrNull { cleanId.contains(it.name.lowercase()) || it.name.lowercase().contains(cleanId) }
            ?: apis.first()
    }

    /**
     * Writes the plugin binary atomically (temp file + rename) and marks it read-only.
     * Making the file read-only is strictly required by Android ART / ClassLoader
     * (Android 10+ / 14+) to prevent SecurityException ("Writable dex file is not allowed").
     */
    private fun writePluginBytesAtomically(pluginFile: File, cs3Data: ByteArray) {
        if (pluginBytesUpToDate(pluginFile, cs3Data)) {
            runCatching { pluginFile.setReadOnly() }
            return
        }
        val parent = pluginFile.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            error("Unable to create plugin cache dir: ${parent.absolutePath}")
        }
        if (pluginFile.exists()) {
            runCatching { pluginFile.setWritable(true) }
            pluginFile.delete()
        }
        val tmp = File(parent, "${pluginFile.name}.tmp")
        if (tmp.exists()) tmp.delete()
        tmp.writeBytes(cs3Data)
        if (!tmp.renameTo(pluginFile)) {
            runCatching { pluginFile.setWritable(true) }
            runCatching { pluginFile.writeBytes(cs3Data) }.getOrThrow()
        }
        runCatching { if (tmp.exists()) tmp.delete() }
        runCatching { pluginFile.setReadOnly() }
    }

    private fun pluginBytesUpToDate(pluginFile: File, cs3Data: ByteArray): Boolean {
        if (!pluginFile.isFile || pluginFile.length() != cs3Data.size.toLong()) return false
        return runCatching { pluginFile.readBytes().contentEquals(cs3Data) }.getOrDefault(false)
    }

    private fun invokePluginLoad(cls: Class<*>, instance: BasePlugin) {
        val ctx = resolveContext()
        val loadWithContext = cls.methods.firstOrNull { method ->
            method.name == "load" &&
                method.parameterTypes.size == 1 &&
                isContextCompatible(method.parameterTypes[0])
        }
        if (loadWithContext != null && ctx != null) {
            val invoked = runCatching { loadWithContext.invoke(instance, ctx) }
                .onFailure { log.w(it) { "Plugin load(Context) failed, falling back to load()" } }
                .isSuccess
            if (invoked) return
        }
        runCatching { instance.load() }
    }

    private fun isContextCompatible(paramType: Class<*>): Boolean =
        runCatching {
            Class.forName("android.content.Context").isAssignableFrom(paramType)
        }.getOrDefault(false)

    fun clearCache() {
        loadedApis.clear()
    }
}

