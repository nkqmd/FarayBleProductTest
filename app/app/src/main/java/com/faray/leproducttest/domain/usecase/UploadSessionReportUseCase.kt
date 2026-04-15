package com.faray.leproducttest.domain.usecase

import com.faray.leproducttest.domain.repository.AuthRepository
import com.faray.leproducttest.domain.repository.ResultUploadRepository
import com.faray.leproducttest.domain.repository.SessionRepository
import com.faray.leproducttest.model.UploadProductionResultRequest
import com.faray.leproducttest.model.UploadProductionResultResponse
import com.faray.leproducttest.model.UploadRecord
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class UploadSessionReportUseCase(
    private val authRepository: AuthRepository,
    private val resultUploadRepository: ResultUploadRepository,
    private val sessionRepository: SessionRepository
) {

    suspend operator fun invoke(sessionId: String, request: UploadProductionResultRequest): Result<UploadRecord> = runCatching {
        val accessToken = authRepository.getValidAccessToken().getOrThrow()
        val response = resultUploadRepository.upload(accessToken = accessToken, request = request).getOrThrow()
        val uploadRecord = UploadRecord(
            sessionId = sessionId,
            batchId = request.batchId,
            reportId = response.reportId,
            reportDigest = response.reportDigest,
            uploadId = response.uploadId,
            duplicate = response.duplicate,
            uploadedAt = parseUploadedAt(response.uploadTime),
            message = response.message
        )
        resultUploadRepository.saveUploadRecord(uploadRecord)
        sessionRepository.markSessionUploaded(sessionId)
        uploadRecord
    }

    private fun parseUploadedAt(uploadTime: String?): Long {
        if (uploadTime.isNullOrBlank()) {
            return System.currentTimeMillis()
        }
        return runCatching { OffsetDateTime.parse(uploadTime, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli() }
            .getOrElse { System.currentTimeMillis() }
    }
}
