package com.faray.leproducttest.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.faray.leproducttest.data.local.entity.BatchMacEntity

@Dao
interface BatchMacDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<BatchMacEntity>): List<Long>

    @Query("SELECT EXISTS(SELECT 1 FROM batch_mac WHERE batchId = :batchId AND macValue = :macValue)")
    suspend fun exists(batchId: String, macValue: Long): Boolean

    @Query("SELECT COUNT(*) FROM batch_mac WHERE batchId = :batchId")
    suspend fun countByBatchId(batchId: String): Int

    @Query("SELECT macValue FROM batch_mac WHERE batchId = :batchId")
    suspend fun getAllMacValues(batchId: String): List<Long>

    @Query("DELETE FROM batch_mac WHERE batchId = :batchId")
    suspend fun deleteByBatchId(batchId: String)
}
