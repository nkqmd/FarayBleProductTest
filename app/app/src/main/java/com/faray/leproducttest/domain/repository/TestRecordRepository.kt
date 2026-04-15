package com.faray.leproducttest.domain.repository

import com.faray.leproducttest.model.TestExecutionResult
import com.faray.leproducttest.model.SessionStatistics
import com.faray.leproducttest.model.UploadFailRecord
import com.faray.leproducttest.model.UploadInvalidRecord
import com.faray.leproducttest.model.UploadSuccessRecord

interface TestRecordRepository {
    suspend fun saveExecutionResult(result: TestExecutionResult)
    suspend fun hasFinalRecord(batchId: String, parsedMac: Long): Boolean
    suspend fun getSessionStatistics(sessionId: String): SessionStatistics
    suspend fun getBatchStatistics(batchId: String): SessionStatistics
    suspend fun getSuccessRecords(sessionId: String): List<UploadSuccessRecord>
    suspend fun getSuccessRecordsByBatch(batchId: String): List<UploadSuccessRecord>
    suspend fun getFailRecords(sessionId: String): List<UploadFailRecord>
    suspend fun getFailRecordsByBatch(batchId: String): List<UploadFailRecord>
    suspend fun getInvalidRecords(sessionId: String): List<UploadInvalidRecord>
    suspend fun getInvalidRecordsByBatch(batchId: String): List<UploadInvalidRecord>
    suspend fun countSuccessBySession(sessionId: String): Int
    suspend fun countFailBySession(sessionId: String): Int
    suspend fun countInvalidBySession(sessionId: String): Int
}
