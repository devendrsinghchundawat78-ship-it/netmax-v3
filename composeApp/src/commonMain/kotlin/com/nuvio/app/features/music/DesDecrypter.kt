package com.nuvio.app.features.music

internal expect object DesDecrypter {
    fun decrypt(base64Ciphertext: String, keyString: String = "38346591"): String?
}
