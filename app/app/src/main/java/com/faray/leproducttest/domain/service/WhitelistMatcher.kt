package com.faray.leproducttest.domain.service

interface WhitelistMatcher {
    suspend fun prepare(batchId: String)
    suspend fun contains(batchId: String, macValue: Long): Boolean
    suspend fun clear(batchId: String)
    suspend fun clearAll()
}
