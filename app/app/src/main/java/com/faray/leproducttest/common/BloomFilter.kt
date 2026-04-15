package com.faray.leproducttest.common

class BloomFilter(
    private val bitSize: Int,
    private val hashFunctions: Int = 3
) {
    private val bits = LongArray((bitSize + Long.SIZE_BITS - 1) / Long.SIZE_BITS)

    fun put(value: Long) {
        repeat(hashFunctions) { index ->
            val bitIndex = hash(value, index)
            bits[bitIndex / Long.SIZE_BITS] =
                bits[bitIndex / Long.SIZE_BITS] or (1L shl (bitIndex % Long.SIZE_BITS))
        }
    }

    fun mightContain(value: Long): Boolean {
        repeat(hashFunctions) { index ->
            val bitIndex = hash(value, index)
            val present = bits[bitIndex / Long.SIZE_BITS] and (1L shl (bitIndex % Long.SIZE_BITS)) != 0L
            if (!present) {
                return false
            }
        }
        return true
    }

    private fun hash(value: Long, seed: Int): Int {
        var mixed = value xor ((seed.toLong() + 1L) * 0x9E3779B9L)
        mixed = mixed xor (mixed ushr 30)
        mixed *= 0x4CF5AD432745937FL
        mixed = mixed xor (mixed ushr 27)
        mixed *= 0x369DEA0F31A53F85L
        mixed = mixed xor (mixed ushr 31)
        return (mixed and Long.MAX_VALUE).rem(bitSize.toLong()).toInt()
    }
}
