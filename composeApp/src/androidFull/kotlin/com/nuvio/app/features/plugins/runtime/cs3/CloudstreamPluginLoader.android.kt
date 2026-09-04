package com.nuvio.app.features.plugins.runtime.cs3

import co.touchlab.kermit.Logger
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.nuvio.app.features.plugins.PluginStorage
import dalvik.system.DexClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

actual object CloudstreamPluginLoader {
    private val log = Logger.withTag("CS3PluginLoader")
    private val loadedApis = ConcurrentHashMap<String, MainAPI>()
    private var pluginCacheDir: File? = null

    fun init(cacheDir: File) {
        pluginCacheDir = File(cacheDir, "cs3_plugins").apply { mkdirs() }
    }

    actual suspend fun loadApi(scraperId: String, cs3Data: ByteArray): MainAPI? = withContext(Dispatchers.IO) {
        loadedApis[scraperId]?.let { return@withContext it }

        if (cs3Data.isEmpty()) return@withContext null

        try {
            val cacheDir = pluginCacheDir ?: File(System.getProperty("java.io.tmpdir") ?: "/tmp", "cs3_plugins").apply { mkdirs() }
            val pluginFile = File(cacheDir, "${scraperId.hashCode()}_plugin.cs3")
            if (!pluginFile.exists() || pluginFile.length() != cs3Data.size.toLong()) {
                pluginFile.writeBytes(cs3Data)
            }

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
                        pluginInstance.load()
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
                                            instance.load()
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

            api
        } catch (e: Throwable) {
            log.e(e) { "Failed to load CloudStream plugin $scraperId" }
            null
        }
    }

    actual fun clearCache() {
        loadedApis.clear()
    }
}
