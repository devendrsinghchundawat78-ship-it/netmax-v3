package com.nuvio.app.features.music

import com.nuvio.app.features.plugins.cryptointerop.CCCrypt
import com.nuvio.app.features.plugins.cryptointerop.kCCAlgorithmDES
import com.nuvio.app.features.plugins.cryptointerop.kCCDecrypt
import com.nuvio.app.features.plugins.cryptointerop.kCCOptionECBMode
import com.nuvio.app.features.plugins.cryptointerop.kCCOptionPKCS7Padding
import com.nuvio.app.features.plugins.cryptointerop.kCCSuccess
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.Foundation.NSData
import platform.Foundation.NSDataBase64DecodingIgnoreUnknownCharacters
import platform.Foundation.NSISOLatin1StringEncoding
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.posix.size_tVar

internal actual object DesDecrypter {
    @OptIn(ExperimentalForeignApi::class)
    actual fun decrypt(base64Ciphertext: String, keyString: String): String? = runCatching {
        val trimmed = base64Ciphertext.trim()
        if (trimmed.isEmpty()) return null

        val data = NSData.create(
            base64Encoding = trimmed
        ) ?: return null

        val dataBytes = data.bytes ?: return null
        val dataLen = data.length.toInt()
        if (dataLen == 0) return null

        val keyBytes = keyString.encodeToByteArray()
        val outBuffer = ByteArray(dataLen + 8)

        memScoped {
            val moved = alloc<size_tVar>()
            val status = keyBytes.usePinned { pinnedKey ->
                outBuffer.usePinned { pinnedOut ->
                    CCCrypt(
                        op = kCCDecrypt,
                        alg = kCCAlgorithmDES,
                        options = kCCOptionPKCS7Padding or kCCOptionECBMode,
                        key = pinnedKey.addressOf(0),
                        keyLength = keyBytes.size.toULong(),
                        iv = null,
                        dataIn = dataBytes,
                        dataInLength = dataLen.toULong(),
                        dataOut = pinnedOut.addressOf(0),
                        dataOutAvailable = outBuffer.size.toULong(),
                        dataOutMoved = moved.ptr
                    )
                }
            }

            if (status == kCCSuccess) {
                val outLen = moved.value.toInt()
                outBuffer.decodeToString(0, outLen).trim()
            } else {
                null
            }
        }
    }.getOrNull()
}
