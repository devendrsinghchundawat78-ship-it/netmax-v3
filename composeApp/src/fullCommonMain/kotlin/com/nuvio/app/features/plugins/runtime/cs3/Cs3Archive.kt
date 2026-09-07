package com.nuvio.app.features.plugins.runtime.cs3

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Read-only helpers for the CloudStream `.cs3` container (a zip holding
 * `manifest.json` + `classes.dex`, optionally `resources.zip`).
 *
 * `manifest.json` carries the plugin class name, but plenty of community repos ship
 * the file without it, so the class names are also recoverable straight from the dex
 * header (class_defs -> type_ids -> string_ids) instead of guessing from the file name.
 */
internal object Cs3Archive {
    const val MANIFEST_ENTRY = "manifest.json"
    const val DEX_ENTRY = "classes.dex"
    const val RESOURCES_ENTRY = "resources.zip"

    fun contains(zipBytes: ByteArray): Boolean = entryNames(zipBytes).isNotEmpty()

    fun entryText(zipBytes: ByteArray, name: String): String? =
        entryBytes(zipBytes, name)?.toString(Charsets.UTF_8)

    fun entryBytes(zipBytes: ByteArray, name: String): ByteArray? {
        if (zipBytes.size < 4 || zipBytes[0] != 'P'.code.toByte() || zipBytes[1] != 'K'.code.toByte()) return null
        return runCatching {
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { stream ->
                while (true) {
                    val entry = stream.nextEntry ?: break
                    if (entry.name == name && !entry.isDirectory) return@use stream.readBytes()
                }
                null
            }
        }.getOrNull()
    }

    fun entryNames(zipBytes: ByteArray): List<String> = runCatching {
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { stream ->
            buildList {
                while (true) {
                    val entry = stream.nextEntry ?: break
                    add(entry.name)
                }
            }
        }
    }.getOrDefault(emptyList())

    /**
     * @return every class name defined by a `classes.dex` payload, in declaration order.
     * Returns an empty list for anything that is not a dex file.
     */
    fun dexClassNames(dex: ByteArray): List<String> = runCatching {
        if (dex.size < 0x70) return@runCatching emptyList()
        val magic = dex[0].toInt() and 0xFF
        // "dex\n035\0" style header
        if (magic != 'd'.code) return@runCatching emptyList()

        fun u4(offset: Int): Int =
            (dex[offset].toInt() and 0xFF) or
                ((dex[offset + 1].toInt() and 0xFF) shl 8) or
                ((dex[offset + 2].toInt() and 0xFF) shl 16) or
                ((dex[offset + 3].toInt() and 0xFF) shl 24)

        val stringIdsSize = u4(0x38)
        val stringIdsOff = u4(0x3C)
        val typeIdsOff = u4(0x44)
        val classDefsSize = u4(0x60)
        val classDefsOff = u4(0x64)
        if (stringIdsSize <= 0 || classDefsSize <= 0 || classDefsSize > 20_000) return@runCatching emptyList()

        fun utf8(offset: Int): String {
            // MUTF-8 with uleb128 length prefix
            var index = offset
            var length = 0
            var shift = 0
            while (index < dex.size) {
                val byte = dex[index].toInt() and 0xFF
                index++
                length = length or ((byte and 0x7F) shl shift)
                if (byte and 0x80 == 0) break
                shift += 7
                if (shift > 21) break
            }
            val end = (index + length).coerceAtMost(dex.size)
            return String(dex, index, (end - index).coerceAtLeast(0), Charsets.UTF_8)
        }

        val names = ArrayList<String>(classDefsSize)
        for (i in 0 until classDefsSize) {
            val item = classDefsOff + i * 32
            if (item + 4 > dex.size) break
            // class_def_item.class_idx -> type_ids[type_idx].descriptor_idx -> string_ids[idx].data_off
            val typeIdx = u4(item)
            if (typeIdx < 0 || typeIdx >= u4(0x40)) break
            val stringIdx = u4(typeIdsOff + typeIdx * 4)
            if (stringIdx < 0 || stringIdx >= stringIdsSize) break
            val descriptor = utf8(u4(stringIdsOff + stringIdx * 4))
            if (descriptor.length > 2 && descriptor.startsWith("L") && descriptor.endsWith(";")) {
                names.add(descriptor.substring(1, descriptor.length - 1).replace('/', '.'))
            }
        }
        names
    }.getOrDefault(emptyList())
}
