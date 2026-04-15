package com.faray.leproducttest.domain.usecase

import com.faray.leproducttest.domain.repository.AuthRepository
import com.faray.leproducttest.domain.repository.ResultUploadRepository
import com.faray.leproducttest.model.UploadBatchResultRequest
import com.faray.leproducttest.model.UploadRecord
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class UploadBatchReportUseCase(
    private val authRepository: AuthRepository,
    private val resultUploadRepository: ResultUploadRepository
) {

    suspend operator fun invoke(request: UploadBatchResultRequest): Result<UploadRecord> = runCatching {
        val accessToken = authRepository.getValidAccessToken().getOrThrow()
        val response = resultUploadRepository.uploadBatch(accessToken = accessToken, request = request).getOrThrow()
        val uploadRecord = UploadRecord(
            sessionId = batchUploadScope(request.batchId),
            batchId = request.batchId,
            reportId = response.batchReportId,
            reportDigest = response.batchReportDigest,
            uploadId = response.uploadId,
            duplicate = response.duplicate,
            uploadedAt = parseUploadedAt(response.uploadedAt),
            message = response.message
        )
        resultUploadRepository.saveBatchUploadRecord(uploadRecord)
        uploadRecord
    }

    private fun parseUploadedAt(uploadTime: String?): Long {
        if (uploadTime.isNullOrBlank()) {
            return System.currentTimeMillis()
        }
        return runCatching {
            OffsetDateTime.parse(uploadTime, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli()
        }.getOrElse {
            System.currentTimeMillis()
        }
    }

    companion object {
        fun batchUploadScope(batchId: String): String = "__batch__:$batchId"
    }
}
