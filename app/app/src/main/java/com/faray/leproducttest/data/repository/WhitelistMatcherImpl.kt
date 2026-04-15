package com.faray.leproducttest.data.repository

import com.faray.leproducttest.common.BloomFilter
import com.faray.leproducttest.data.local.dao.BatchMacDao
import com.faray.leproducttest.domain.service.WhitelistMatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max

class WhitelistMatcherImpl(
    private val batchMacDao: BatchMacDao
) : WhitelistMatcher {

    private val mutex = Mutex()
    private val states = linkedMapOf<String, MatcherState>()

    override suspend fun prepare(batchId: String) {
        mutex.withLock {
            val macValues = batchMacDao.getAllMacValues(batchId)
            val bloomSize = max(1_024, macValues.size * 10)
            val bloomFilter = BloomFilter(bitSize = bloomSize)
            macValues.forEach(bloomFilter::put)
            states[batchId] = MatcherState(
                bloomFilter = bloomFilter,
                cache = object : LinkedHashMap<Long, Boolean>(CACHE_CAPACITY, 0.75f, true) {
                    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Boolean>?): Boolean {
                        return size > CACHE_CAPACITY
                    }
                }
            )
        }
    }

    override suspend fun contains(batchId: String, macValue: Long): Boolean {
        ensurePrepared(batchId)
        val state = mutex.withLock { states[batchId] } ?: return false

        mutex.withLock {
            state.cache[macValue]?.let { return it }
        }

        if (!state.bloomFilter.mightContain(macValue)) {
            mutex.withLock { state.cache[macValue] = false }
            return false
        }

        val exact = batchMacDao.exists(batchId = batchId, macValue = macValue)
        mutex.withLock { state.cache[macValue] = exact }
        return exact
    }

    override suspend fun clear(batchId: String) {
        mutex.withLock { states.remove(batchId) }
    }

    override suspend fun clearAll() {
        mutex.withLock { states.clear() }
    }

    private suspend fun ensurePrepared(batchId: String) {
        val prepared = mutex.withLock { states.containsKey(batchId) }
        if (!prepared) {
            prepare(batchId)
        }
    }

    private data class MatcherState(
        val bloomFilter: BloomFilter,
        val cache: LinkedHashMap<Long, Boolean>
    )

    private companion object {
        const val CACHE_CAPACITY = 10_000
    }
}
