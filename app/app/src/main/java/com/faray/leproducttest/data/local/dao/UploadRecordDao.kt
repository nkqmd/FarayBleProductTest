package com.faray.leproducttest.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.faray.leproducttest.data.local.entity.UploadRecordEntity

@Dao
interface UploadRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: UploadRecordEntity)

    @Query("SELECT * FROM upload_record WHERE sessionId = :sessionId ORDER BY uploadedAt DESC LIMIT 1")
    suspend fun getLatestBySession(sessionId: String): UploadRecordEntity?

    @Query("SELECT * FROM upload_record WHERE batchId = :batchId AND sessionId = :sessionId ORDER BY uploadedAt DESC LIMIT 1")
    suspend fun getLatestByBatchAndSession(batchId: String, sessionId: String): UploadRecordEntity?
}
