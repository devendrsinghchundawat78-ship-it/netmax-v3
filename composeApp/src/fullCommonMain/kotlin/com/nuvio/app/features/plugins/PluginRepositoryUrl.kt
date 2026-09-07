package com.nuvio.app.features.plugins

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Repository url handling for the plugin screen, kept free of app state so it can be
 * reasoned about (and tested) on its own:
 *
 *  - CloudStream's own "add repository" links (`cloudstreamrepo://…`, `cloudstream://…`)
 *    are pasted straight out of a browser or the upstream apps, so they have to be
 *    unwrapped instead of being treated as a host name.
 *  - a repo may point at `repo.json`, whose `pluginLists` entries hold the real provider
 *    arrays; those lists are followed and merged here.
 */
private val repoPayloadJson = Json { ignoreUnknownKeys = true }

private val DEEP_LINK_PREFIXES = listOf(
    "cloudstreamrepo://",
    "cloudstreamrepo:/",
    "cloudstream://",
    "cloudstream:/",
)

/**
 * @return the absolute manifest url to fetch, or null when nothing usable was typed in.
 */
internal fun normalizeCloudStreamRepoUrl(rawUrl: String): String? {
    var value = rawUrl.trim()
    if (value.isEmpty()) return null
    var fromDeepLink = false

    // cloudstreamrepo://https://host/repo.json, cloudstream://host/repo.json and
    // cloudstreamrepo://add?url=<encoded> all show up in the wild; every one of them
    // is just the repository url behind a scheme the OS hands to the app.
    for (prefix in DEEP_LINK_PREFIXES) {
        if (value.startsWith(prefix, ignoreCase = true)) {
            value = value.substring(prefix.length).trim()
            fromDeepLink = true
            break
        }
    }
    if (value.isNotEmpty() && !value.startsWith("http", ignoreCase = true)) {
        val urlParam = Regex("""[?&]url=([^&]+)""").find(value)?.groupValues?.get(1)
        value = when {
            urlParam != null -> percentDecode(urlParam)
            value.contains("%3A", ignoreCase = true) -> percentDecode(value)
            else -> value.removePrefix("add/").removePrefix("add:")
        }
        value = value.trim()
    }
    if (value.isEmpty()) return null

    val withScheme = when {
        value.startsWith("http://") || value.startsWith("https://") -> value
        else -> "https://$value"
    }

    val withoutFragment = withScheme.substringBefore("#")
    val query = withoutFragment.substringAfter("?", "")
    val path = withoutFragment.substringBefore("?").trimEnd('/')
    val host = withScheme.substringAfter("://", "").substringBefore("/")
    if (host.isBlank()) return null
    // Only links we unwrapped are checked for looking like a host, so a wrapper that
    // carried no payload at all ("cloudstreamrepo://add?url=") is refused instead of
    // being fetched as https://add/manifest.json.
    if (fromDeepLink && !host.contains('.') && !host.contains(':')) return null

    val manifestPath = when {
        path.endsWith("/manifest.json") || path.endsWith("/repo.json") ||
            path.endsWith("/plugins.json") || path.endsWith(".cs3") || path.endsWith(".json") -> path
        else -> "$path/manifest.json"
    }
    return if (query.isEmpty()) manifestPath else "$manifestPath?$query"
}

/**
 * Follows `pluginLists` so a `repo.json` indirection resolves to the provider arrays.
 * Every list is fetched and merged (upstream splits e.g. tv / nsfw providers into
 * separate files); on a per-list failure the payloads that did load are kept, and when
 * nothing loads the original text is returned unchanged so the caller reports its
 * usual error instead of an empty list.
 */
internal suspend fun resolveCloudStreamRepoPayload(
    payload: String,
    fetch: suspend (url: String) -> String,
): String {
    val root = parseJsonOrNull(payload) as? JsonObject ?: return payload
    val lists = (root["pluginLists"] as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        ?.filter { it.isNotBlank() }
        .orEmpty()
    if (lists.isEmpty()) return payload

    val merged = ArrayList<JsonElement>()
    for (url in lists) {
        val text = runCatching { fetch(url) }.getOrNull() ?: continue
        when (val parsed = parseJsonOrNull(text)) {
            is JsonArray -> merged.addAll(parsed)
            is JsonObject -> {
                val nested = (parsed["plugins"] as? JsonArray)
                    ?: ((parsed["data"] as? JsonObject)?.get("plugins") as? JsonArray)
                if (nested != null) merged.addAll(nested) else merged.add(parsed)
            }
            else -> Unit
        }
    }
    if (merged.isEmpty()) return payload
    return JsonArray(merged).toString()
}

private fun parseJsonOrNull(text: String): JsonElement? =
    runCatching { repoPayloadJson.parseToJsonElement(text) }.getOrNull()

/** Minimal percent decoding, so this stays free of `java.net` on every target. */
internal fun percentDecode(value: String): String {
    if (!value.contains('%')) return value
    val bytes = ArrayList<Byte>(value.length)
    var i = 0
    while (i < value.length) {
        val c = value[i]
        if (c == '%' && i + 2 < value.length) {
            val decoded = value.substring(i + 1, i + 3).toIntOrNull(16)
            if (decoded != null) {
                bytes.add(decoded.toByte())
                i += 3
                continue
            }
        }
        for (b in c.toString().encodeToByteArray()) bytes.add(b)
        i++
    }
    return String(bytes.toByteArray(), Charsets.UTF_8)
}
