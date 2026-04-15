package com.faray.leproducttest.domain.usecase

import com.faray.leproducttest.BuildConfig
import com.faray.leproducttest.common.ReportDigestBuilder
import com.faray.leproducttest.domain.repository.BatchRepository
import com.faray.leproducttest.domain.repository.SessionRepository
import com.faray.leproducttest.domain.repository.TestRecordRepository
import com.faray.leproducttest.model.UploadProductionResultDigestSource
import com.faray.leproducttest.model.UploadProductionResultRequest
import com.faray.leproducttest.model.UploadStatistics
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class BuildSessionReportUseCase(
    private val sessionRepository: SessionRepository,
    private val batchRepository: BatchRepository,
    private val testRecordRepository: TestRecordRepository
) {

    private val isoFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    suspend operator fun invoke(sessionId: String): Result<UploadProductionResultRequest> = runCatching {
        val session = sessionRepository.getSession(sessionId)
            ?: error("Session not found: $sessionId")
        val endedAt = session.endedAt ?: error("Session is not finished yet: $sessionId")
        val batchProfile = batchRepository.getBatchProfile(session.batchId)
            ?: error("Batch profile not found: ${session.batchId}")
        val statistics = testRecordRepository.getSessionStatistics(sessionId)
        val successRecords = testRecordRepository.getSuccessRecords(sessionId)
        val failRecords = testRecordRepository.getFailRecords(sessionId)
        val invalidRecords = testRecordRepository.getInvalidRecords(sessionId)
        val uploadStatistics = UploadStatistics(
            expectedCount = batchProfile.expectedCount,
            actualCount = statistics.actualCount,
            successCount = statistics.successCount,
            failCount = statistics.failCount,
            successRate = statistics.successRate
        )
        val digestSource = UploadProductionResultDigestSource(
            batchId = session.batchId.trim(),
            factoryId = session.factoryId.trim(),
            appVersion = BuildConfig.VERSION_NAME,
            testStartTime = formatIsoTime(session.startedAt),
            testEndTime = formatIsoTime(endedAt),
            statistics = uploadStatistics,
            successRecords = successRecords,
            failRecords = failRecords,
            invalid = invalidRecords
        )
        val reportDigest = ReportDigestBuilder.buildReportDigest(digestSource)
        UploadProductionResultRequest(
            reportId = ReportDigestBuilder.buildReportId(session.batchId, reportDigest),
            reportDigest = reportDigest,
            batchId = digestSource.batchId,
            factoryId = digestSource.factoryId,
            appVersion = digestSource.appVersion,
            testStartTime = digestSource.testStartTime,
            testEndTime = digestSource.testEndTime,
            statistics = digestSource.statistics,
            successRecords = digestSource.successRecords,
            failRecords = digestSource.failRecords,
            invalid = digestSource.invalid
        )
    }

    private fun formatIsoTime(timeMillis: Long): String {
        return Instant.ofEpochMilli(timeMillis)
            .atZone(ZoneId.systemDefault())
            .format(isoFormatter)
    }
}
