package com.nuvio.app.features.streams

private val credentialQueryKeys = setOf(
    "accesskey",
    "accesssignature",
    "accesssig",
    "access_token",
    "accesstoken",
    "auth",
    "authkey",
    "authsig",
    "authsignature",
    "auth_token",
    "authtoken",
    "e",
    "exp",
    "expiration",
    "expire",
    "expires",
    "expiresat",
    "expiresin",
    "expires_in",
    "expiry",
    "hmac",
    "jwt",
    "keypairid",
    "policy",
    "sig",
    "signature",
    "signed",
    "st",
    "t",
    "token",
)

private val credentialKeyFragments = listOf(
    "token",
    "signature",
    "expires",
    "expiry",
)

private val explicitExpirationQueryKeys = setOf(
    "exp",
    "expires",
    "expiresat",
    "expires_at",
    "expiresin",
    "expires_in",
    "expiry",
    "expire",
    "e",
)

internal fun String.hasLikelyExpiringPlaybackCredentials(): Boolean {
    val query = substringAfter('?', missingDelimiterValue = "")
        .substringBefore('#')
        .takeIf { it.isNotBlank() }
        ?: return false

    return query
        .split('&', ';')
        .any { rawParameter ->
            val rawKey = rawParameter
                .substringBefore('=', missingDelimiterValue = "")
                .trim()
                .lowercase()
            if (rawKey.isBlank()) return@any false

            val compactKey = rawKey
                .replace("-", "")
                .replace("_", "")
                .replace(".", "")

            rawKey in credentialQueryKeys ||
                compactKey in credentialQueryKeys ||
                credentialKeyFragments.any { fragment ->
                    rawKey.contains(fragment) || compactKey.contains(fragment)
                }
        }
}

internal fun String.isExplicitlyExpiredUrl(nowEpochMs: Long = epochMs()): Boolean {
    val query = substringAfter('?', missingDelimiterValue = "")
        .substringBefore('#')
        .takeIf { it.isNotBlank() }
        ?: return false

    val parameters = query.split('&', ';')
    for (param in parameters) {
        val parts = param.split('=', limit = 2)
        if (parts.size != 2) continue
        val key = parts[0].trim().lowercase().replace("-", "").replace("_", "").replace(".", "")
        if (key in explicitExpirationQueryKeys) {
            val rawValue = parts[1].trim()
            val parsedSeconds = rawValue.toLongOrNull()
                ?: rawValue.toDoubleOrNull()?.toLong()
            if (parsedSeconds != null) {
                val expirationMs = if (parsedSeconds in 1_000_000_000L..100_000_000_000L) {
                    parsedSeconds * 1000L
                } else if (parsedSeconds > 100_000_000_000L) {
                    parsedSeconds
                } else {
                    null
                }
                if (expirationMs != null && expirationMs <= nowEpochMs) {
                    return true
                }
            }
        }
    }
    return false
}

