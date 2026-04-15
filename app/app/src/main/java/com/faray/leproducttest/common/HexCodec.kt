package com.faray.leproducttest.common

object HexCodec {

    fun parse(raw: String): ByteArray? {
        val normalized = normalize(raw) ?: return null
        return normalized.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    fun normalize(raw: String): String? {
        val compact = raw.trim()
            .replace(" ", "")
            .uppercase()
        if (compact.isEmpty() || compact.length % 2 != 0) {
            return null
        }
        if (!compact.all { it.isDigit() || it in 'A'..'F' }) {
            return null
        }
        return compact
    }

    fun toHex(bytes: ByteArray?): String? {
        return bytes?.joinToString(separator = "") { "%02X".format(it) }
    }
}
