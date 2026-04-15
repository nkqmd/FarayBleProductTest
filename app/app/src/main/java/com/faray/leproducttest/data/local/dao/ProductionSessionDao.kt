package com.faray.leproducttest.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.faray.leproducttest.data.local.entity.ProductionSessionEntity

@Dao
interface ProductionSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ProductionSessionEntity)

    @Query("SELECT * FROM production_session WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getById(sessionId: String): ProductionSessionEntity?

    @Query("SELECT * FROM production_session WHERE status = :status LIMIT 1")
    suspend fun getByStatus(status: String): ProductionSessionEntity?

    @Query("SELECT * FROM production_session WHERE batchId = :batchId AND status IN (:statuses) ORDER BY startedAt DESC LIMIT 1")
    suspend fun getLatestByBatchAndStatuses(batchId: String, statuses: List<String>): ProductionSessionEntity?

    @Query("SELECT * FROM production_session WHERE batchId = :batchId AND status = :status ORDER BY startedAt DESC LIMIT 1")
    suspend fun getLatestByBatchAndStatus(batchId: String, status: String): ProductionSessionEntity?

    @Query("SELECT * FROM production_session WHERE batchId = :batchId AND endedAt IS NOT NULL ORDER BY startedAt ASC")
    suspend fun getFinishedByBatch(batchId: String): List<ProductionSessionEntity>
}
