package com.lagradost.cloudstream3.extractors.helper

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.base64Encode
import com.lagradost.cloudstream3.utils.AppUtils
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object AesHelper {

    fun generateKeyAndIv(
        password: ByteArray,
        salt: ByteArray,
        keyLength: Int = 32,
        ivLength: Int = 16,
        saltLength: Int = salt.size,
        iterations: Int = 1,
    ): Pair<ByteArray, ByteArray>? {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val digestLength = 16
            val targetKeySize = keyLength + ivLength
            val requiredLength = ((targetKeySize + digestLength - 1) / digestLength) * digestLength
            val generatedData = ByteArray(requiredLength)
            var generatedLength = 0

            while (generatedLength < targetKeySize) {
                md.reset()
                if (generatedLength > 0) {
                    md.update(generatedData, generatedLength - digestLength, digestLength)
                }
                md.update(password)
                md.update(salt, 0, saltLength)
                var digest = md.digest()

                for (i in 1 until iterations) {
                    md.reset()
                    md.update(digest)
                    digest = md.digest()
                }

                System.arraycopy(digest, 0, generatedData, generatedLength, digest.size)
                generatedLength += digestLength
            }

            val key = generatedData.copyOfRange(0, keyLength)
            val iv = generatedData.copyOfRange(keyLength, targetKeySize)
            key to iv
        } catch (_: Exception) {
            null
        }
    }

    fun cryptoAESHandler(
        data: String,
        pass: ByteArray,
        encrypt: Boolean = false,
        padding: String = "AES/CBC/PKCS5Padding",
    ): String? {
        return try {
            val parse = AppUtils.tryParseJson<AesData>(data) ?: return null
            val (key, iv) = generateKeyAndIv(
                password = pass,
                salt = parse.s.hexToByteArray(),
                keyLength = 32,
                ivLength = parse.iv.length / 2,
                saltLength = parse.s.length / 2,
            ) ?: return null

            val cipher = Cipher.getInstance(padding)
            val keySpec = SecretKeySpec(key, "AES")
            val ivSpec = IvParameterSpec(iv)

            if (!encrypt) {
                cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
                val cipherText = base64DecodeArray(parse.ct)
                val decrypted = cipher.doFinal(cipherText)
                String(decrypted, Charsets.UTF_8)
            } else {
                cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
                val encrypted = cipher.doFinal(parse.ct.toByteArray(Charsets.UTF_8))
                base64Encode(encrypted)
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun cryptoAESHandler(
        data: String,
        pass: ByteArray,
        encrypt: Boolean = false,
        padding: Boolean = true,
    ): String? {
        val padStr = if (padding) "AES/CBC/PKCS5Padding" else "AES/CBC/NoPadding"
        return cryptoAESHandler(data, pass, encrypt, padStr)
    }

    fun String.hexToByteArray(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    data class AesData(
        @JsonProperty("ct") val ct: String = "",
        @JsonProperty("iv") val iv: String = "",
        @JsonProperty("s") val s: String = "",
    )
}
