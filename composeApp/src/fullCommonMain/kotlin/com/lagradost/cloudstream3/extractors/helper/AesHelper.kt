@file:OptIn(com.lagradost.cloudstream3.InternalAPI::class)
package com.lagradost.cloudstream3.extractors.helper

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.Prerelease
import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.base64Encode
import com.lagradost.cloudstream3.utils.AppUtils
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * CryptoJS / OpenSSL "Salted__" compatible AES-CBC helper. Providers use it for
 * the encrypted embed payloads; implemented on the platform crypto so no extra
 * dependency is pulled in.
 */
object AesHelper {

    suspend fun cryptoAESHandler(
        data: String,
        pass: ByteArray,
        encrypt: Boolean = true,
        padding: Boolean = true,
    ): String? {
        val parse = AppUtils.tryParseJson<AesData>(data) ?: return null
        val (key, iv) = generateKeyAndIv(
            password = pass,
            salt = parse.s.hexToByteArray(),
            ivLength = parse.iv.length / 2,
            saltLength = parse.s.length / 2,
        ) ?: return null

        return runCatching {
            val cipher = Cipher.getInstance(if (padding) "AES/CBC/PKCS5Padding" else "AES/CBC/NoPadding")
            cipher.init(
                if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                IvParameterSpec(iv)
            )
            if (!encrypt) {
                cipher.doFinal(base64DecodeArray(parse.ct)).decodeToString()
            } else {
                base64Encode(cipher.doFinal(parse.ct.encodeToByteArray()))
            }
        }.getOrNull()
    }

    @Deprecated(
        message = "Set padding = false for no padding",
        level = DeprecationLevel.WARNING,
    )
    fun cryptoAESHandler(
        data: String,
        pass: ByteArray,
        encrypt: Boolean = true,
        padding: String,
    ): String? {
        val hasPadding = !padding.endsWith("NoPadding")
        return runBlocking { cryptoAESHandler(data, pass, encrypt, hasPadding) }
    }

    // https://stackoverflow.com/a/41434590/8166854
    fun generateKeyAndIv(
        password: ByteArray,
        salt: ByteArray,
        keyLength: Int = 32,
        ivLength: Int,
        saltLength: Int,
        iterations: Int = 1,
    ): Pair<ByteArray, ByteArray>? {
        return try {
            val digestLength = 16 // MD5 digest is always 16 bytes
            val targetKeySize = keyLength + ivLength
            val requiredLength = (targetKeySize + digestLength - 1) / digestLength * digestLength
            val generatedData = ByteArray(requiredLength)
            var generatedLength = 0
            val md5 = MessageDigest.getInstance("MD5")

            while (generatedLength < targetKeySize) {
                if (generatedLength > 0) {
                    md5.update(generatedData, generatedLength - digestLength, generatedLength)
                }
                md5.update(password)
                md5.update(salt, 0, minOf(saltLength, salt.size))
                val digest = md5.digest()
                digest.copyInto(generatedData, generatedLength)

                var previous = digest
                for (i in 1 until iterations) {
                    md5.reset()
                    md5.update(previous)
                    previous = md5.digest()
                    previous.copyInto(generatedData, generatedLength)
                }

                generatedLength += digestLength
            }

            generatedData.copyOfRange(0, keyLength) to
                generatedData.copyOfRange(keyLength, targetKeySize)
        } catch (_: Exception) {
            null
        }
    }

    fun String.hexToByteArray(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    @Serializable
    private data class AesData(
        @JsonProperty("ct") @SerialName("ct") val ct: String,
        @JsonProperty("iv") @SerialName("iv") val iv: String,
        @JsonProperty("s") @SerialName("s") val s: String,
    )
}
