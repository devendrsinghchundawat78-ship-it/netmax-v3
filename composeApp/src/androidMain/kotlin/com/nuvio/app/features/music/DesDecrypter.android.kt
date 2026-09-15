package com.nuvio.app.features.music

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

internal actual object DesDecrypter {
    actual fun decrypt(base64Ciphertext: String, keyString: String): String? = runCatching {
        val trimmed = base64Ciphertext.trim()
        if (trimmed.isEmpty()) return null
        val keyBytes = keyString.toByteArray(Charsets.UTF_8)
        val keySpec = SecretKeySpec(keyBytes, "DES")
        val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, keySpec)
        val decoded = Base64.decode(trimmed, Base64.DEFAULT)
        val decrypted = cipher.doFinal(decoded)
        String(decrypted, Charsets.UTF_8).trim()
    }.getOrNull()
}
