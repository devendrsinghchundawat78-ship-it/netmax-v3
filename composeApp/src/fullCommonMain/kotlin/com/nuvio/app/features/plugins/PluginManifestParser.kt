package com.nuvio.app.features.plugins

import kotlinx.serialization.json.*

internal object PluginManifestParser {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun parse(payload: String): PluginManifest {
        val trimmed = payload.trim()

        if (trimmed.startsWith("{")) {
            val standardResult = runCatching {
                val manifest = json.decodeFromString<PluginManifest>(payload)
                if (manifest.name.isNotBlank() && manifest.scrapers.isNotEmpty()) {
                    manifest
                } else {
                    null
                }
            }.getOrNull()
            if (standardResult != null) {
                return standardResult
            }
        }

        val rootElement = runCatching { json.parseToJsonElement(payload) }.getOrNull()
        if (rootElement != null) {
            val manifest = parseCloudStreamManifest(rootElement)
            if (manifest != null) {
                return manifest
            }
        }

        val standard = json.decodeFromString<PluginManifest>(payload)
        require(standard.name.isNotBlank()) { "Manifest name is missing" }
        require(standard.version.isNotBlank()) { "Manifest version is missing" }
        require(standard.scrapers.isNotEmpty()) { "Manifest contains no providers" }
        return standard
    }

    private fun parseCloudStreamManifest(root: JsonElement): PluginManifest? {
        val pluginsArray = when (root) {
            is JsonArray -> root
            is JsonObject -> {
                (root["plugins"] ?: root["scrapers"] ?: root["providers"]) as? JsonArray
                    ?: ((root["data"] as? JsonObject)?.let {
                        (it["plugins"] ?: it["scrapers"]) as? JsonArray
                    })
            }
            else -> null
        } ?: return null

        val scrapers = pluginsArray.mapNotNull { elem ->
            val obj = elem as? JsonObject ?: return@mapNotNull null
            val name = (obj["name"] ?: obj["title"])?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val fileName = obj["fileName"]?.jsonPrimitive?.contentOrNull
            val internalName = obj["internalName"]?.jsonPrimitive?.contentOrNull
                ?: fileName?.removeSuffix(".cs3")
                ?: name
            // Repos publish either a full download url or just the file name; both work
            // because the caller retries relative names against the repository base urls.
            val url = listOf("url", "fileUrl", "downloadUrl")
                .firstNotNullOfOrNull { key -> obj[key]?.jsonPrimitive?.contentOrNull }
                ?: fileName
                ?: return@mapNotNull null
            val version = obj["version"]?.jsonPrimitive?.contentOrNull ?: "1"
            val iconUrl = obj["iconUrl"]?.jsonPrimitive?.contentOrNull

            val tvTypes = (obj["tvTypes"] as? JsonArray)?.mapNotNull {
                it.jsonPrimitive.contentOrNull
            }?.takeIf { it.isNotEmpty() }
                ?: obj["tvType"]?.jsonPrimitive?.contentOrNull?.let { listOf(it) }
                ?: listOf("Movie", "TvSeries")

            val supportedTypes = tvTypes.flatMap { mapCloudStreamTvType(it) }.distinct()

            PluginManifestScraper(
                id = internalName,
                name = name,
                description = "CloudStream Provider ($name)",
                version = version,
                filename = url,
                supportedTypes = supportedTypes,
                enabled = true,
                hasSettings = false,
                logo = iconUrl,
                formats = listOf("cs3"),
                supportedFormats = listOf("cs3"),
            )
        }

        if (scrapers.isEmpty()) return null

        val repoName = if (root is JsonObject) {
            root["name"]?.jsonPrimitive?.contentOrNull ?: "CloudStream Repository"
        } else {
            "CloudStream Repository"
        }

        return PluginManifest(
            name = repoName,
            version = "1.0.0",
            description = "CloudStream Providers",
            scrapers = scrapers,
        )
    }

    /** A repo entry can serve both browsable types, so one tag may map to several. */
    private fun mapCloudStreamTvType(tvType: String): List<String> = when (tvType.lowercase()) {
        "movie", "animemovie" -> listOf("movie")
        "tvseries", "anime", "ova", "cartoon", "asiandrama", "documentary" -> listOf("tv")
        "all", "tv" -> listOf("movie", "tv")
        else -> listOf("movie", "tv")
    }
}
