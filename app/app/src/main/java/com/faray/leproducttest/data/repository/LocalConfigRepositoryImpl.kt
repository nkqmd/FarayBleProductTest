package com.faray.leproducttest.data.repository

import com.faray.leproducttest.data.local.dao.LocalConfigDao
import com.faray.leproducttest.data.local.entity.LocalConfigEntity
import com.faray.leproducttest.domain.repository.LocalConfigRepository

class LocalConfigRepositoryImpl(
    private val localConfigDao: LocalConfigDao
) : LocalConfigRepository {

    override suspend fun saveFactoryId(factoryId: String) {
        localConfigDao.upsert(
            LocalConfigEntity(
                key = KEY_FACTORY_ID,
                value = factoryId.trim(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun getFactoryId(): String? {
        return localConfigDao.getByKey(KEY_FACTORY_ID)?.value
    }

    override suspend fun saveLastBatchId(batchId: String) {
        localConfigDao.upsert(
            LocalConfigEntity(
                key = KEY_LAST_BATCH_ID,
                value = batchId.trim(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun getLastBatchId(): String? {
        return localConfigDao.getByKey(KEY_LAST_BATCH_ID)?.value
    }

    private companion object {
        const val KEY_FACTORY_ID = "factory_id"
        const val KEY_LAST_BATCH_ID = "last_batch_id"
    }
}
