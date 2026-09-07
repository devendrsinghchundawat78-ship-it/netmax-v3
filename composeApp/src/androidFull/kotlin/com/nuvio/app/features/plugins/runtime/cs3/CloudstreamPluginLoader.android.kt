package com.nuvio.app.features.plugins.runtime.cs3

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import co.touchlab.kermit.Logger
import com.lagradost.cloudstream3.CloudStreamApp
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
            val cacheDir = resolveCacheDir()
            val pluginFile = File(cacheDir, "${pluginDigestHex("SHA256", scraperId)}_plugin.cs3")
            writePluginBytesAtomically(pluginFile, cs3Data)

            // Providers read the host Application through MainActivity.app /
            // getContext() (prefs, resources, cookies). Nothing else in this app
            // seeds that state, so do it before a single plugin class is touched.
            installHostContext()

            val optDir = File(cacheDir, "opt").apply { mkdirs() }
            val parentClassLoader = CloudstreamPluginLoader::class.java.classLoader
            val dexClassLoader = DexClassLoader(
                pluginFile.absolutePath,
                optDir.absolutePath,
                null,
                parentClassLoader
            )

            var pluginClassName: String? = null
            var requiresResources = false

            // 1. Try reading manifest.json inside cs3 zip
            val manifestText = Cs3Archive.entryText(pluginFile.readBytes(), Cs3Archive.MANIFEST_ENTRY)
            if (manifestText != null) {
                runCatching {
                    val json = JSONObject(manifestText)
                    pluginClassName = json.optString("pluginClassName").takeIf { !it.isNullOrBlank() }
                    requiresResources = json.optBoolean("requiresResources", false)
                }
            }

            var api: MainAPI? = null

            if (pluginClassName != null) {
                runCatching {
                    val cls = dexClassLoader.loadClass(pluginClassName)
                    if (BasePlugin::class.java.isAssignableFrom(cls)) {
                        val pluginInstance = cls.getDeclaredConstructor().newInstance() as BasePlugin
                        attachResources(pluginInstance, pluginFile, cacheDir, requiresResources)
                        invokePluginLoad(cls, pluginInstance)
                        api = pluginInstance.registeredApis.firstOrNull()
                    } else if (MainAPI::class.java.isAssignableFrom(cls)) {
                        api = cls.getDeclaredConstructor().newInstance() as MainAPI
                    }
                }.onFailure { log.w(it) { "Failed to instantiate plugin class from manifest: $pluginClassName" } }
            }

            if (api == null) {
                // 2. Plenty of community repos ship a manifest without a usable
                //    pluginClassName, so read the class list straight out of
                //    classes.dex and take the first provider the loader can build.
                val dexNames = runCatching {
                    Cs3Archive.entryBytes(pluginFile.readBytes(), Cs3Archive.DEX_ENTRY)
                        ?.let { Cs3Archive.dexClassNames(it) }
                }.getOrNull().orEmpty()
                for (className in dexNames) {
                    if (api != null) break
                    // Skip lambda / inner classes: a plugin is always a top level class.
                    if (className.contains('$')) continue
                    runCatching {
                        val cls = dexClassLoader.loadClass(className)
                        if (cls.isInterface) return@runCatching
                        if (BasePlugin::class.java.isAssignableFrom(cls)) {
                            val instance = cls.getDeclaredConstructor().newInstance() as BasePlugin
                            // No manifest to read requiresResources from, but attaching is
                            // a no-op unless the .cs3 actually ships a resources.zip.
                            attachResources(instance, pluginFile, cacheDir, requiresResources = true)
                            invokePluginLoad(cls, instance)
                            if (instance.registeredApis.isNotEmpty()) {
                                api = instance.registeredApis.firstOrNull()
                            }
                        } else if (MainAPI::class.java.isAssignableFrom(cls)) {
                            api = cls.getDeclaredConstructor().newInstance() as MainAPI
                        }
                    }
                }
            }

            // MainAPI.init() is what the CloudStream app calls over every provider
            // after loading plugins (name / url overrides); without it a provider
            // keeps whatever defaults its constructor set.
            api?.let { readyApi -> runCatching { readyApi.init() } }

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
            installHostContext()
            val appCtx = currentApplicationContext()
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

    /**
     * The dex and its extracted `resources.zip` live in the app cache dir; nobody has
     * to call [init] for that, the application context is enough.
     */
    private fun resolveCacheDir(): File {
        pluginCacheDir?.let { return it }
        val base = runCatching { currentApplicationContext()?.cacheDir }.getOrNull()
            ?: File(System.getProperty("java.io.tmpdir") ?: "/tmp")
        return File(base, "cs3_plugins").apply { mkdirs() }.also { pluginCacheDir = it }
    }

    private fun currentApplicationContext(): Context? = runCatching {
        Class.forName("android.app.ActivityThread")
            .getMethod("currentApplication")
            .invoke(null) as? Context
    }.getOrNull()

    /** Hands the plugin the same static context a normal CloudStream app sets up. */
    private fun installHostContext() {
        val ctx = currentApplicationContext() ?: return
        runCatching { Plugin.hostContext = ctx }
        runCatching { CloudStreamApp.ctx = ctx }
        runCatching { com.lagradost.api.setContext(ctx) }
    }

    /**
     * A .cs3 can carry its own `resources.zip`; upstream points an AssetManager at it
     * so `getIdentifier()` inside the plugin resolves against the plugin, not the app.
     */
    private fun attachResources(
        instance: BasePlugin,
        pluginFile: File,
        cacheDir: File,
        requiresResources: Boolean,
    ) {
        if (!requiresResources) return
        val plugin = instance as? Plugin ?: return
        runCatching {
            val packed = Cs3Archive.entryBytes(pluginFile.readBytes(), Cs3Archive.RESOURCES_ENTRY)
                ?: return@runCatching
            val target = File(cacheDir, "${pluginFile.name}.resources.zip")
            if (!target.isFile || target.length() != packed.size.toLong()) target.writeBytes(packed)
            // Both are hidden platform APIs, exactly like upstream's plugin loader uses them.
            val assets = AssetManager::class.java.getDeclaredConstructor()
                .apply { isAccessible = true }
                .newInstance()
            AssetManager::class.java
                .getDeclaredMethod("addAssetPath", String::class.java)
                .apply { isAccessible = true }
                .invoke(assets, target.absolutePath)
            val hostResources = currentApplicationContext()?.resources
            plugin.resources = if (hostResources != null) {
                Resources(assets, hostResources.displayMetrics, hostResources.configuration)
            } else {
                Resources(assets, null, null)
            }
        }.onFailure { log.w(it) { "Unable to attach resources for " + plugin.javaClass.name } }
    }

    private fun isContextCompatible(paramType: Class<*>): Boolean =
        runCatching {
            Class.forName("android.content.Context").isAssignableFrom(paramType)
        }.getOrDefault(false)

    fun clearCache() {
        loadedApis.clear()
    }
}
