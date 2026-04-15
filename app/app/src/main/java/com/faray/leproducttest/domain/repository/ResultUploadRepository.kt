package com.faray.leproducttest.domain.repository

import com.faray.leproducttest.model.UploadBatchResultRequest
import com.faray.leproducttest.model.UploadBatchResultResponse
import com.faray.leproducttest.model.UploadProductionResultRequest
import com.faray.leproducttest.model.UploadProductionResultResponse
import com.faray.leproducttest.model.UploadRecord

interface ResultUploadRepository {
    suspend fun upload(accessToken: String, request: UploadProductionResultRequest): Result<UploadProductionResultResponse>
    suspend fun uploadBatch(accessToken: String, request: UploadBatchResultRequest): Result<UploadBatchResultResponse>
    suspend fun saveUploadRecord(record: UploadRecord)
    suspend fun saveBatchUploadRecord(record: UploadRecord)
    suspend fun getLatestUploadRecord(sessionId: String): UploadRecord?
    suspend fun getLatestBatchUploadRecord(batchId: String): UploadRecord?
}
