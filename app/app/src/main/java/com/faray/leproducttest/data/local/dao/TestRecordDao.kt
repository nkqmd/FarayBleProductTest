package com.faray.leproducttest.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.faray.leproducttest.data.local.entity.TestRecordEntity

@Dao
interface TestRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TestRecordEntity)

    @Query(
        "SELECT EXISTS(" +
            "SELECT 1 FROM test_record " +
            "WHERE batchId = :batchId AND parsedMac = :parsedMac AND finalStatus IN ('PASS', 'FAIL')" +
            ")"
    )
    suspend fun hasFinalRecord(batchId: String, parsedMac: Long): Boolean

    @Query("SELECT COUNT(*) FROM test_record WHERE sessionId = :sessionId AND finalStatus = 'PASS'")
    suspend fun countPassBySession(sessionId: String): Int

    @Query("SELECT COUNT(*) FROM test_record WHERE sessionId = :sessionId AND finalStatus = 'FAIL'")
    suspend fun countFailBySession(sessionId: String): Int

    @Query("SELECT COUNT(*) FROM test_record WHERE sessionId = :sessionId AND finalStatus = 'INVALID_DEVICE'")
    suspend fun countInvalidBySession(sessionId: String): Int

    @Query("SELECT COUNT(*) FROM test_record WHERE sessionId = :sessionId AND finalStatus IN ('PASS', 'FAIL')")
    suspend fun countActualBySession(sessionId: String): Int

    @Query("SELECT COUNT(*) FROM test_record WHERE batchId = :batchId AND finalStatus = 'PASS'")
    suspend fun countPassByBatch(batchId: String): Int

    @Query("SELECT COUNT(*) FROM test_record WHERE batchId = :batchId AND finalStatus = 'FAIL'")
    suspend fun countFailByBatch(batchId: String): Int

    @Query("SELECT COUNT(*) FROM test_record WHERE batchId = :batchId AND finalStatus = 'INVALID_DEVICE'")
    suspend fun countInvalidByBatch(batchId: String): Int

    @Query("SELECT COUNT(*) FROM test_record WHERE batchId = :batchId AND finalStatus IN ('PASS', 'FAIL')")
    suspend fun countActualByBatch(batchId: String): Int

    @Query("SELECT * FROM test_record WHERE sessionId = :sessionId AND finalStatus = 'PASS' ORDER BY parsedMac ASC, endedAt ASC")
    suspend fun getPassRecords(sessionId: String): List<TestRecordEntity>

    @Query("SELECT * FROM test_record WHERE sessionId = :sessionId AND finalStatus = 'FAIL' ORDER BY parsedMac ASC, endedAt ASC, reason ASC")
    suspend fun getFailRecords(sessionId: String): List<TestRecordEntity>

    @Query("SELECT * FROM test_record WHERE sessionId = :sessionId AND finalStatus = 'INVALID_DEVICE' ORDER BY parsedMac ASC, endedAt ASC")
    suspend fun getInvalidRecords(sessionId: String): List<TestRecordEntity>

    @Query("SELECT * FROM test_record WHERE batchId = :batchId AND finalStatus = 'PASS' ORDER BY endedAt ASC, parsedMac ASC")
    suspend fun getPassRecordsByBatch(batchId: String): List<TestRecordEntity>

    @Query("SELECT * FROM test_record WHERE batchId = :batchId AND finalStatus = 'FAIL' ORDER BY endedAt ASC, parsedMac ASC, reason ASC")
    suspend fun getFailRecordsByBatch(batchId: String): List<TestRecordEntity>

    @Query("SELECT * FROM test_record WHERE batchId = :batchId AND finalStatus = 'INVALID_DEVICE' ORDER BY endedAt ASC, parsedMac ASC")
    suspend fun getInvalidRecordsByBatch(batchId: String): List<TestRecordEntity>
}
