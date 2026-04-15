package com.faray.leproducttest.domain.repository

interface LocalConfigRepository {
    suspend fun saveFactoryId(factoryId: String)
    suspend fun getFactoryId(): String?
    suspend fun saveLastBatchId(batchId: String)
    suspend fun getLastBatchId(): String?
}
