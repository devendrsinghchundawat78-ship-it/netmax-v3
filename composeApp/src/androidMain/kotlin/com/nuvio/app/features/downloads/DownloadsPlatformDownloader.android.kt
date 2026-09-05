package com.nuvio.app.features.downloads

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val DEFAULT_DOWNLOAD_USER_AGENT = "Mozilla/5.0 (Android) AppleWebKit/537.36 Chrome/131 Mobile Safari/537.36"

private val downloadHttpClient = OkHttpClient.Builder()
    .connectTimeout(60, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .build()

internal actual object DownloadsPlatformDownloader {
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    actual fun start(
        request: DownloadPlatformRequest,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
        onSuccess: (localFileUri: String, totalBytes: Long?) -> Unit,
        onFailure: (message: String) -> Unit,
    ): DownloadsTaskHandle {
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)
        var call: Call? = null

        scope.launch {
            val context = appContext
            if (context == null) {
                onFailure(runBlocking { getString(Res.string.downloads_error_not_initialized) })
                return@launch
            }

            val downloadsDir = File(context.filesDir, "downloads").apply { mkdirs() }
            val destination = File(downloadsDir, request.destinationFileName)
            val tempFile = File(downloadsDir, "${request.destinationFileName}.part")

            try {
                if (request.sourceUrl.isHlsPlaylistUrl()) {
                    downloadHlsPlaylist(
                        playlistUrl = request.sourceUrl,
                        downloadsDir = downloadsDir,
                        destination = destination,
                        tempFile = tempFile,
                        headers = buildDownloadHeaders(request.sourceHeaders),
                        onProgress = onProgress,
                        onSuccess = onSuccess,
                        registerCall = { call = it },
                    )
                    return@launch
                }

                var resumeFromBytes = tempFile.takeIf { it.exists() }?.length()?.coerceAtLeast(0L) ?: 0L

                fun buildRequest(rangeStart: Long?): Request {
                    val requestBuilder = Request.Builder().url(request.sourceUrl)
                    val userAgent = request.sourceHeaders.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value
                    val accept = request.sourceHeaders.entries.firstOrNull { it.key.equals("Accept", ignoreCase = true) }?.value
                    requestBuilder.header("User-Agent", userAgent ?: DEFAULT_DOWNLOAD_USER_AGENT)
                    requestBuilder.header("Accept", accept ?: "video/*,application/octet-stream,*/*;q=0.8")
                    requestBuilder.header("Accept-Encoding", "identity")
                    request.sourceHeaders.forEach { (key, value) ->
                        if (!key.equals("User-Agent", ignoreCase = true) &&
                            !key.equals("Accept", ignoreCase = true) &&
                            !key.equals("Accept-Encoding", ignoreCase = true)
                        ) {
                            requestBuilder.header(key, value)
                        }
                    }
                    if (rangeStart != null && rangeStart > 0L) {
                        requestBuilder.header("Range", "bytes=$rangeStart-")
                    }
                    return requestBuilder.get().build()
                }

                var attemptedRangeRequest = resumeFromBytes > 0L
                var httpRequest = buildRequest(if (attemptedRangeRequest) resumeFromBytes else null)
                call = downloadHttpClient.newCall(httpRequest)
                var response = call?.execute() ?: error(
                    runBlocking { getString(Res.string.downloads_error_request_failed) },
                )

                if (attemptedRangeRequest && response.code == 416) {
                    response.close()
                    tempFile.delete()
                    resumeFromBytes = 0L
                    attemptedRangeRequest = false
                    httpRequest = buildRequest(null)
                    call = downloadHttpClient.newCall(httpRequest)
                    response = call?.execute() ?: error(
                        runBlocking { getString(Res.string.downloads_error_request_failed) },
                    )
                }

                response.use { response ->
                    if (!response.isSuccessful) {
                        error(
                            runBlocking {
                                getString(Res.string.downloads_error_http_failed, response.code)
                            },
                        )
                    }

                    val isPartialResume = attemptedRangeRequest && response.code == 206 && resumeFromBytes > 0L
                    val appendToTemp = isPartialResume
                    val startingBytes = if (appendToTemp) resumeFromBytes else 0L

                    if (!appendToTemp && tempFile.exists()) {
                        tempFile.delete()
                    }

                    val body = response.body ?: error(
                        runBlocking { getString(Res.string.downloads_error_empty_body) },
                    )
                    val totalBytes = resolveTotalBytes(
                        startingBytes = startingBytes,
                        isPartialResume = isPartialResume,
                        contentRangeHeader = response.header("Content-Range"),
                        contentLength = body.contentLength().takeIf { it > 0L },
                    )
                    var downloadedBytes = startingBytes
                    onProgress(downloadedBytes, totalBytes)

                    body.byteStream().use { input ->
                        FileOutputStream(tempFile, appendToTemp).use { output ->
                            val buffer = ByteArray(16 * 1024)
                            while (true) {
                                ensureActive()
                                val read = input.read(buffer)
                                if (read <= 0) break
                                output.write(buffer, 0, read)
                                downloadedBytes += read.toLong()
                                onProgress(downloadedBytes, totalBytes)
                            }
                            output.flush()
                        }
                    }

                    if (destination.exists()) {
                        destination.delete()
                    }
                    if (!tempFile.renameTo(destination)) {
                        tempFile.copyTo(destination, overwrite = true)
                        tempFile.delete()
                    }

                    val finalSize = destination.length()
                    onSuccess(destination.toURI().toString(), totalBytes ?: finalSize)
                }
            } catch (error: Throwable) {
                onFailure(error.message ?: runBlocking { getString(Res.string.download_failed) })
            }
        }

        job.invokeOnCompletion {
            call?.cancel()
        }

        return AndroidDownloadsTaskHandle(job)
    }

    actual fun removeFile(localFileUri: String?): Boolean {
        if (localFileUri.isNullOrBlank()) return false
        val file = localFileUri.toLocalFileOrNull() ?: return false
        return runCatching { file.delete() }.getOrDefault(false)
    }

    actual fun removePartialFile(destinationFileName: String): Boolean {
        val context = appContext ?: return false
        val downloadsDir = File(context.filesDir, "downloads")
        val tempFile = File(downloadsDir, "$destinationFileName.part")
        if (!tempFile.exists()) return true
        return runCatching { tempFile.delete() }.getOrDefault(false)
    }

    actual fun resolveLocalFileUri(localFileUri: String?, destinationFileName: String): String? {
        localFileUri
            ?.toLocalFileOrNull()
            ?.takeIf { it.exists() }
            ?.let { return it.toURI().toString() }

        val context = appContext ?: return null
        val fileName = destinationFileName.trim().takeIf { it.isNotBlank() }
            ?: localFileUri
                ?.toLocalFileOrNull()
                ?.name
                ?.takeIf { it.isNotBlank() }
            ?: return null
        val downloadsDir = File(context.filesDir, "downloads")
        val localFile = File(downloadsDir, fileName)
        return localFile.takeIf { it.exists() }?.toURI()?.toString()
    }

    actual fun openDownloadsDirectory(): Boolean {
        val context = appContext ?: return false
        val downloadsDir = File(context.filesDir, "downloads").apply { mkdirs() }
        val uri = runCatching {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                downloadsDir,
            )
        }.getOrNull() ?: return false

        val intents = listOf(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "resource/folder")
            },
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "vnd.android.document/directory")
            },
            Intent(Intent.ACTION_VIEW).apply {
                data = uri
            },
        )

        return intents.any { intent ->
            intent.addCategory(Intent.CATEGORY_DEFAULT)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)

            runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
        }
    }
}

private class AndroidDownloadsTaskHandle(
    private val job: Job,
) : DownloadsTaskHandle {
    override fun cancel() {
        job.cancel()
    }
}

private fun String.toLocalFileOrNull(): File? {
    return runCatching {
        if (startsWith("file:")) {
            File(URI(this))
        } else {
            File(this)
        }
    }.getOrNull()
}

private fun resolveTotalBytes(
    startingBytes: Long,
    isPartialResume: Boolean,
    contentRangeHeader: String?,
    contentLength: Long?,
): Long? {
    parseContentRangeTotal(contentRangeHeader)?.let { return it }
    val normalizedLength = contentLength?.takeIf { it > 0L } ?: return null
    return if (isPartialResume && startingBytes > 0L) {
        startingBytes + normalizedLength
    } else {
        normalizedLength
    }
}

private fun parseContentRangeTotal(headerValue: String?): Long? {
    val value = headerValue?.trim().orEmpty()
    if (value.isBlank()) return null
    val slashIndex = value.lastIndexOf('/')
    if (slashIndex == -1 || slashIndex == value.lastIndex) return null
    val totalPart = value.substring(slashIndex + 1).trim()
    if (totalPart == "*") return null
    return totalPart.toLongOrNull()?.takeIf { it > 0L }
}

private const val HLS_MAX_PLAYLIST_BYTES = 5L * 1024 * 1024
private const val HLS_MAX_SEGMENT_BYTES = 256L * 1024 * 1024
private const val HLS_MAX_SEGMENTS = 8000

private data class HlsKey(
    val method: String,
    val uri: String?,
    val iv: ByteArray?,
)

private data class HlsSegment(
    val uri: String,
    val rangeStart: Long?,
    val rangeLength: Long?,
    val key: HlsKey?,
    val sequence: Long,
) {
    fun rangeEndInclusive(): Long? =
        if (rangeStart != null && rangeLength != null && rangeLength > 0L) {
            rangeStart + rangeLength - 1L
        } else {
            null
        }
}

private data class HlsParsedMedia(
    val initSegmentUri: String?,
    val segments: List<HlsSegment>,
    val sawEndList: Boolean,
    val isFmp4: Boolean,
)

private fun buildDownloadHeaders(sourceHeaders: Map<String, String>): Headers {
    val builder = Headers.Builder()
    val userAgent = sourceHeaders.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value
    val accept = sourceHeaders.entries.firstOrNull { it.key.equals("Accept", ignoreCase = true) }?.value
    builder["User-Agent"] = userAgent ?: DEFAULT_DOWNLOAD_USER_AGENT
    builder["Accept"] = accept ?: "video/*,application/octet-stream,*/*;q=0.8"
    builder["Accept-Encoding"] = "identity"
    sourceHeaders.forEach { (key, value) ->
        if (!key.equals("User-Agent", ignoreCase = true) &&
            !key.equals("Accept", ignoreCase = true) &&
            !key.equals("Accept-Encoding", ignoreCase = true)
        ) {
            builder[key] = value
        }
    }
    return builder.build()
}

/**
 * Downloads an HLS (`.m3u8`) stream into a single local file: master playlists
 * resolve to the highest-bandwidth variant, then the init segment (if any) and
 * every media segment are fetched sequentially and concatenated. AES-128
 * encrypted segments are decrypted; other encryption schemes fail with a clear
 * message instead of producing an unplayable file.
 */
private suspend fun CoroutineScope.downloadHlsPlaylist(
    playlistUrl: String,
    downloadsDir: File,
    destination: File,
    tempFile: File,
    headers: Headers,
    onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    onSuccess: (localFileUri: String, totalBytes: Long?) -> Unit,
    registerCall: (Call?) -> Unit,
) {
    // HLS downloads always restart from scratch: a partial segment stream
    // cannot be resumed by byte range.
    if (tempFile.exists()) {
        tempFile.delete()
    }

    val playlistText = fetchHlsText(playlistUrl, headers, registerCall)
        ?: error(runBlocking { getString(Res.string.downloads_error_hls_playlist) })

    var mediaPlaylistUrl = playlistUrl
    var mediaPlaylistText = playlistText
    val variants = parseHlsMasterVariants(playlistUrl, playlistText)
    if (variants.isNotEmpty()) {
        val best = variants.maxByOrNull { it.bandwidth } ?: variants.first()
        mediaPlaylistUrl = best.uri
        mediaPlaylistText = fetchHlsText(best.uri, headers, registerCall)
            ?: error(runBlocking { getString(Res.string.downloads_error_hls_playlist) })
    }

    val parsed = parseHlsMediaPlaylist(mediaPlaylistUrl, mediaPlaylistText)
    if (!parsed.sawEndList) {
        error(runBlocking { getString(Res.string.downloads_error_hls_live) })
    }
    if (parsed.segments.isEmpty() && parsed.initSegmentUri == null) {
        error(runBlocking { getString(Res.string.downloads_error_hls_segments) })
    }
    val segments = parsed.segments.take(HLS_MAX_SEGMENTS)

    val finalDestination = if (parsed.isFmp4 && !destination.name.endsWith(".mp4", ignoreCase = true)) {
        File(downloadsDir, destination.nameWithoutExtension + ".mp4")
    } else {
        destination
    }

    val keyCache = mutableMapOf<String, ByteArray>()
    var downloadedBytes = 0L
    onProgress(0L, null)

    FileOutputStream(tempFile, false).use { output ->
        parsed.initSegmentUri?.let { initUri ->
            ensureActive()
            val bytes = fetchHlsBytes(initUri, headers, null, null, registerCall)
                ?: error(runBlocking { getString(Res.string.downloads_error_hls_playlist) })
            output.write(bytes)
            downloadedBytes += bytes.size.toLong()
            onProgress(downloadedBytes, null)
        }
        for (segment in segments) {
            ensureActive()
            val key = segment.key
            if (key != null && key.method != "NONE" && key.method != "AES-128") {
                error(runBlocking { getString(Res.string.downloads_error_hls_encrypted) })
            }
            var bytes = fetchHlsBytes(segment.uri, headers, segment.rangeStart, segment.rangeEndInclusive(), registerCall)
                ?: error(runBlocking { getString(Res.string.downloads_error_hls_playlist) })
            if (key != null && key.method == "AES-128") {
                val keyUri = key.uri
                    ?: error(runBlocking { getString(Res.string.downloads_error_hls_encrypted) })
                val keyBytes = keyCache.getOrPut(keyUri) {
                    fetchHlsBytes(keyUri, headers, null, null, registerCall)?.takeIf { it.size == 16 }
                        ?: error(runBlocking { getString(Res.string.downloads_error_hls_encrypted) })
                }
                if (bytes.size % 16 != 0) {
                    error(runBlocking { getString(Res.string.downloads_error_hls_encrypted) })
                }
                val iv = key.iv ?: hlsSequenceIv(segment.sequence)
                bytes = runCatching { aes128CbcDecrypt(keyBytes, iv, bytes) }.getOrElse {
                    error(runBlocking { getString(Res.string.downloads_error_hls_encrypted) })
                }
            }
            output.write(bytes)
            downloadedBytes += bytes.size.toLong()
            onProgress(downloadedBytes, null)
        }
        output.flush()
    }

    if (destination != finalDestination && destination.exists()) {
        destination.delete()
    }
    if (finalDestination.exists()) {
        finalDestination.delete()
    }
    if (!tempFile.renameTo(finalDestination)) {
        tempFile.copyTo(finalDestination, overwrite = true)
        tempFile.delete()
    }

    val finalSize = finalDestination.length()
    onSuccess(finalDestination.toURI().toString(), finalSize)
}

private fun fetchHlsText(
    url: String,
    headers: Headers,
    registerCall: (Call?) -> Unit,
): String? {
    val call = downloadHttpClient.newCall(Request.Builder().url(url).headers(headers).get().build())
    registerCall(call)
    call.execute().use { response ->
        if (!response.isSuccessful) return null
        val body = response.body ?: return null
        val bytes = body.byteStream().use { it.readCapped(HLS_MAX_PLAYLIST_BYTES) }
        return bytes.toString(Charsets.UTF_8).takeIf { it.contains("#EXTM3U") }
    }
}

private fun fetchHlsBytes(
    url: String,
    headers: Headers,
    rangeStart: Long?,
    rangeEndInclusive: Long?,
    registerCall: (Call?) -> Unit,
): ByteArray? {
    val builder = Request.Builder().url(url).headers(headers)
    if (rangeStart != null && rangeStart >= 0L) {
        val end = rangeEndInclusive?.let { "-$it" } ?: "-"
        builder.header("Range", "bytes=$rangeStart$end")
    }
    val call = downloadHttpClient.newCall(builder.get().build())
    registerCall(call)
    call.execute().use { response ->
        if (!response.isSuccessful) return null
        val body = response.body ?: return null
        return body.byteStream().use { it.readCapped(HLS_MAX_SEGMENT_BYTES) }
    }
}

private fun java.io.InputStream.readCapped(maxBytes: Long): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    var total = 0L
    while (true) {
        val read = read(buffer)
        if (read <= 0) break
        total += read
        if (total > maxBytes) error("Response exceeded ${maxBytes} bytes")
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}

private data class HlsVariant(val bandwidth: Long, val uri: String)

private fun parseHlsMasterVariants(baseUrl: String, text: String): List<HlsVariant> {
    val variants = mutableListOf<HlsVariant>()
    var pendingBandwidth = 0L
    var expectUri = false
    for (rawLine in text.lineSequence()) {
        val line = rawLine.trim()
        when {
            line.startsWith("#EXT-X-STREAM-INF:") -> {
                pendingBandwidth = parseHlsAttributeList(line.substringAfter(':'))["BANDWIDTH"]?.toLongOrNull() ?: 0L
                expectUri = true
            }
            expectUri && line.isNotEmpty() && !line.startsWith("#") -> {
                variants += HlsVariant(pendingBandwidth, resolveHlsUrl(baseUrl, line))
                expectUri = false
            }
        }
    }
    return variants
}

private fun parseHlsMediaPlaylist(baseUrl: String, text: String): HlsParsedMedia {
    var initSegmentUri: String? = null
    var currentKey: HlsKey? = null
    var pendingRange: Pair<Long?, Long>? = null
    val nextRangeStartByUri = mutableMapOf<String, Long>()
    var sawEndList = false
    var isFmp4 = false
    val mediaSequenceBase = text.lineSequence().firstNotNullOfOrNull { rawLine ->
        val line = rawLine.trim()
        if (line.startsWith("#EXT-X-MEDIA-SEQUENCE:")) {
            line.substringAfter(':').trim().toLongOrNull()
        } else {
            null
        }
    } ?: 0L
    val segments = mutableListOf<HlsSegment>()
    var sequence = 0L
    for (rawLine in text.lineSequence()) {
        val line = rawLine.trim()
        when {
            line.isEmpty() -> Unit
            line == "#EXT-X-ENDLIST" -> sawEndList = true
            line.startsWith("#EXT-X-MAP:") -> {
                parseHlsAttributeList(line.substringAfter(':'))["URI"]?.let { uri ->
                    initSegmentUri = resolveHlsUrl(baseUrl, uri)
                    isFmp4 = true
                }
            }
            line.startsWith("#EXT-X-KEY:") -> {
                val attrs = parseHlsAttributeList(line.substringAfter(':'))
                currentKey = HlsKey(
                    method = attrs["METHOD"]?.uppercase() ?: "NONE",
                    uri = attrs["URI"]?.let { resolveHlsUrl(baseUrl, it) },
                    iv = attrs["IV"]?.let(::parseHlsIv),
                )
            }
            line.startsWith("#EXT-X-BYTERANGE:") -> {
                val spec = line.substringAfter(':').trim()
                val length = spec.substringBefore('@').toLongOrNull()
                val start = spec.substringAfter('@', "").toLongOrNull()
                if (length != null && length > 0L) {
                    pendingRange = start to length
                }
            }
            line.startsWith("#") -> Unit
            else -> {
                val resolved = resolveHlsUrl(baseUrl, line)
                val lower = resolved.substringBefore('?').substringBefore('#').lowercase()
                if (lower.endsWith(".m4s") || lower.endsWith(".mp4") ||
                    lower.endsWith(".m4a") || lower.endsWith(".cmfv") || lower.endsWith(".cmfa")
                ) {
                    isFmp4 = true
                }
                val (explicitStart, length) = pendingRange ?: (null to null)
                pendingRange = null
                val start = explicitStart ?: nextRangeStartByUri[resolved]
                if (length != null && length > 0L) {
                    nextRangeStartByUri[resolved] = (start ?: 0L) + length
                }
                segments += HlsSegment(
                    uri = resolved,
                    rangeStart = start.takeIf { length != null },
                    rangeLength = length,
                    key = currentKey,
                    sequence = mediaSequenceBase + sequence,
                )
                sequence++
            }
        }
    }
    return HlsParsedMedia(initSegmentUri, segments, sawEndList, isFmp4)
}

private fun parseHlsAttributeList(spec: String): Map<String, String> {
    val result = mutableMapOf<String, String>()
    val key = StringBuilder()
    val value = StringBuilder()
    var readingKey = true
    var inQuotes = false
    fun flush() {
        if (key.isNotEmpty()) {
            result[key.toString().trim().uppercase()] = value.toString()
        }
        key.clear()
        value.clear()
        readingKey = true
    }
    for (c in spec) {
        when {
            readingKey && c == '=' -> readingKey = false
            !readingKey && c == '"' -> inQuotes = !inQuotes
            !readingKey && !inQuotes && c == ',' -> flush()
            readingKey -> key.append(c)
            else -> value.append(c)
        }
    }
    flush()
    return result
}

private fun resolveHlsUrl(baseUrl: String, reference: String): String {
    val ref = reference.trim()
    if (ref.startsWith("http://", ignoreCase = true) || ref.startsWith("https://", ignoreCase = true)) {
        return ref
    }
    return runCatching { URI(baseUrl).resolve(ref).toString() }.getOrNull() ?: ref
}

private fun parseHlsIv(raw: String): ByteArray? {
    var hex = raw.trim().removePrefix("0x").removePrefix("0X")
    if (hex.length > 32 || hex.any { !it.isDigit() && it.lowercaseChar() !in 'a'..'f' }) return null
    hex = hex.padStart(32, '0')
    return runCatching {
        ByteArray(16) { index -> hex.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
    }.getOrNull()
}

private fun hlsSequenceIv(sequence: Long): ByteArray {
    val iv = ByteArray(16)
    for (i in 0 until 8) {
        iv[8 + i] = (sequence shr ((7 - i) * 8)).toByte()
    }
    return iv
}

private fun aes128CbcDecrypt(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/CBC/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
    return cipher.doFinal(data)
}
