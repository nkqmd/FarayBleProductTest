package com.faray.leproducttest.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.faray.leproducttest.data.local.entity.BatchProfileEntity

@Dao
interface BatchProfileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: BatchProfileEntity)

    @Query("SELECT * FROM batch_profile WHERE batchId = :batchId LIMIT 1")
    suspend fun getByBatchId(batchId: String): BatchProfileEntity?

    @Query("UPDATE batch_profile SET rssiMin = :rssiMin WHERE batchId = :batchId")
    suspend fun updateRssiMin(batchId: String, rssiMin: Int)
}
