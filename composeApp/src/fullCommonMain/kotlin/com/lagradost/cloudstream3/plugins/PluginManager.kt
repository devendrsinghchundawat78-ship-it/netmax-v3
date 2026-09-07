@file:OptIn(com.lagradost.cloudstream3.InternalAPI::class)
package com.lagradost.cloudstream3.plugins

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A loaded .cs3 as upstream describes it. Providers use it for update checks and
 * to locate their own file, so the field order matches the original data class.
 */
@Serializable
data class PluginData(
    @JsonProperty("internalName") @SerialName("internalName") val internalName: String,
    @JsonProperty("url") @SerialName("url") val url: String?,
    @JsonProperty("isOnline") @SerialName("isOnline") val isOnline: Boolean,
    @JsonProperty("filePath") @SerialName("filePath") val filePath: String,
    @JsonProperty("version") @SerialName("version") val version: Int,
) {
    fun toSitePlugin(): SitePlugin =
        SitePlugin(
            filePath = filePath,
            status = SitePlugin.PROVIDER_STATUS_OK,
            version = maxOf(1, version),
            apiVersion = 1,
            name = internalName,
            internalName = internalName,
            authors = emptyList(),
            fileName = filePath.substringAfterLast("/"),
            repositoryUrl = url,
            shortDescription = null,
            iconUrl = null,
            fileSize = null,
            fileHash = null
        )
}
