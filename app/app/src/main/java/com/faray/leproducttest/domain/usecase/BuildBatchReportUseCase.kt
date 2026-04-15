package com.faray.leproducttest.domain.usecase

import com.faray.leproducttest.BuildConfig
import com.faray.leproducttest.common.UploadJsonCodec
import com.faray.leproducttest.domain.repository.BatchRepository
import com.faray.leproducttest.domain.repository.SessionRepository
import com.faray.leproducttest.domain.repository.TestRecordRepository
import com.faray.leproducttest.model.BatchUploadStatistics
import com.faray.leproducttest.model.UploadBatchResultFilePayload
import com.faray.leproducttest.model.UploadBatchResultRequest
import com.faray.leproducttest.model.UploadIncludedSession
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class BuildBatchReportUseCase(
    private val sessionRepository: SessionRepository,
    private val batchRepository: BatchRepository,
    private val testRecordRepository: TestRecordRepository
) {

    private val isoFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    private val utcCompactFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

    suspend operator fun invoke(
        batchId: String,
        factoryId: String
    ): Result<UploadBatchResultRequest> = runCatching {
        val safeBatchId = batchId.trim()
        val safeFactoryId = factoryId.trim()
        require(safeBatchId.isNotBlank()) { "Batch ID is required" }
        require(safeFactoryId.isNotBlank()) { "Factory ID is required" }

        val batchProfile = batchRepository.getBatchProfile(safeBatchId)
            ?: error("Batch profile not found: $safeBatchId")
        val sessions = sessionRepository.getFinishedSessions(safeBatchId)
            .filter { it.endedAt != null }
        val statistics = testRecordRepository.getBatchStatistics(safeBatchId)
        val successRecords = testRecordRepository.getSuccessRecordsByBatch(safeBatchId)
        val failRecords = testRecordRepository.getFailRecordsByBatch(safeBatchId)
        val invalidRecords = testRecordRepository.getInvalidRecordsByBatch(safeBatchId)

        require(
            statistics.actualCount > 0 || statistics.invalidCount > 0
        ) { "No local batch result is available for upload" }
        require(sessions.isNotEmpty()) { "No finished session is available for batch upload" }

        val aggregateStartAt = sessions.minOf { it.startedAt }
        val aggregateEndAt = sessions.maxOf { it.endedAt ?: it.startedAt }
        val payload = UploadBatchResultFilePayload(
            batchId = safeBatchId,
            factoryId = safeFactoryId,
            appVersion = BuildConfig.VERSION_NAME,
            aggregateStartTime = formatIsoTime(aggregateStartAt),
            aggregateEndTime = formatIsoTime(aggregateEndAt),
            statistics = BatchUploadStatistics(
                expectedCount = batchProfile.expectedCount,
                actualCount = statistics.actualCount,
                successCount = statistics.successCount,
                failCount = statistics.failCount,
                invalidCount = statistics.invalidCount,
                successRate = statistics.successRate
            ),
            includedSessions = sessions.map { session ->
                UploadIncludedSession(
                    sessionId = session.sessionId,
                    testStartTime = formatIsoTime(session.startedAt),
                    testEndTime = formatIsoTime(session.endedAt ?: session.startedAt)
                )
            },
            successRecords = successRecords,
            failRecords = failRecords,
            invalid = invalidRecords
        )
        val fileJson = UploadJsonCodec.encodeBatchFilePayload(payload)
        val fileBytes = fileJson.toByteArray(StandardCharsets.UTF_8)
        val digest = sha256(fileBytes)
        val batchReportId = buildBatchReportId(
            batchId = safeBatchId,
            factoryId = safeFactoryId,
            aggregateEndAt = aggregateEndAt,
            digest = digest
        )

        UploadBatchResultRequest(
            batchReportId = batchReportId,
            batchReportDigest = digest,
            batchId = safeBatchId,
            factoryId = safeFactoryId,
            appVersion = payload.appVersion,
            aggregateStartTime = payload.aggregateStartTime,
            aggregateEndTime = payload.aggregateEndTime,
            fileName = "batch_result.json",
            fileJson = fileJson,
            fileBytes = fileBytes
        )
    }

    private fun formatIsoTime(timeMillis: Long): String {
        return Instant.ofEpochMilli(timeMillis)
            .atZone(ZoneId.systemDefault())
            .format(isoFormatter)
    }

    private fun buildBatchReportId(
        batchId: String,
        factoryId: String,
        aggregateEndAt: Long,
        digest: String
    ): String {
        val utcStamp = Instant.ofEpochMilli(aggregateEndAt)
            .atOffset(ZoneOffset.UTC)
            .format(utcCompactFormatter)
        return "${batchId}_${factoryId}_${utcStamp}_${digest.take(8)}"
    }

    private fun sha256(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { "%02x".format(it) }
    }
}
