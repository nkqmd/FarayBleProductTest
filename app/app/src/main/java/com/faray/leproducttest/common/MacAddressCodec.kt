package com.faray.leproducttest.common

object MacAddressCodec {

    fun parseToLong(raw: String): Long? {
        val normalized = normalize(raw) ?: return null
        return normalized.toLongOrNull(16)
    }

    fun isUppercaseHex12(raw: String): Boolean {
        return raw.length == 12 && raw.all { it.isDigit() || it in 'A'..'F' }
    }

    fun normalize(raw: String): String? {
        val compact = raw.trim()
            .replace(":", "")
            .replace("-", "")
            .uppercase()
        if (compact.length != 12) {
            return null
        }
        if (!compact.all { it.isDigit() || it in 'A'..'F' }) {
            return null
        }
        return compact
    }

    fun toHex(macValue: Long): String {
        return "%012X".format(macValue)
    }
}
