package com.faray.leproducttest.data.repository

import com.faray.leproducttest.data.local.dao.TestRecordDao
import com.faray.leproducttest.data.local.entity.TestRecordEntity
import com.faray.leproducttest.domain.repository.TestRecordRepository
import com.faray.leproducttest.model.SessionStatistics
import com.faray.leproducttest.model.TestExecutionResult
import com.faray.leproducttest.model.UploadFailRecord
import com.faray.leproducttest.model.UploadInvalidRecord
import com.faray.leproducttest.model.UploadSuccessRecord
import com.faray.leproducttest.common.MacAddressCodec
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TestRecordRepositoryImpl(
    private val testRecordDao: TestRecordDao
) : TestRecordRepository {

    private val isoFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    override suspend fun saveExecutionResult(result: TestExecutionResult) {
        testRecordDao.upsert(
            TestRecordEntity(
                sessionId = result.sessionId,
                batchId = result.batchId,
                parsedMac = result.parsedMac,
                deviceAddress = result.deviceAddress,
                advName = result.advName,
                rssi = result.rssi,
                finalStatus = result.finalStatus.name,
                success = result.success,
                reason = result.reason,
                startedAt = result.startedAt,
                endedAt = result.endedAt,
                createdAt = result.endedAt
            )
        )
    }

    override suspend fun hasFinalRecord(batchId: String, parsedMac: Long): Boolean {
        return testRecordDao.hasFinalRecord(batchId = batchId, parsedMac = parsedMac)
    }

    override suspend fun getSessionStatistics(sessionId: String): SessionStatistics {
        val actualCount = testRecordDao.countActualBySession(sessionId)
        val successCount = testRecordDao.countPassBySession(sessionId)
        val failCount = testRecordDao.countFailBySession(sessionId)
        val invalidCount = testRecordDao.countInvalidBySession(sessionId)
        val successRate = if (actualCount == 0) 0.0 else successCount * 100.0 / actualCount
        return SessionStatistics(
            actualCount = actualCount,
            successCount = successCount,
            failCount = failCount,
            invalidCount = invalidCount,
            successRate = successRate
        )
    }

    override suspend fun getBatchStatistics(batchId: String): SessionStatistics {
        val actualCount = testRecordDao.countActualByBatch(batchId)
        val successCount = testRecordDao.countPassByBatch(batchId)
        val failCount = testRecordDao.countFailByBatch(batchId)
        val invalidCount = testRecordDao.countInvalidByBatch(batchId)
        val successRate = if (actualCount == 0) 0.0 else successCount * 100.0 / actualCount
        return SessionStatistics(
            actualCount = actualCount,
            successCount = successCount,
            failCount = failCount,
            invalidCount = invalidCount,
            successRate = successRate
        )
    }

    override suspend fun getSuccessRecords(sessionId: String): List<UploadSuccessRecord> {
        return testRecordDao.getPassRecords(sessionId).map { record ->
            UploadSuccessRecord(
                sessionId = record.sessionId,
                mac = MacAddressCodec.toHex(record.parsedMac),
                time = formatIsoTime(record.endedAt)
            )
        }
    }

    override suspend fun getSuccessRecordsByBatch(batchId: String): List<UploadSuccessRecord> {
        return testRecordDao.getPassRecordsByBatch(batchId).map { record ->
            UploadSuccessRecord(
                sessionId = record.sessionId,
                mac = MacAddressCodec.toHex(record.parsedMac),
                time = formatIsoTime(record.endedAt)
            )
        }
    }

    override suspend fun getFailRecords(sessionId: String): List<UploadFailRecord> {
        return testRecordDao.getFailRecords(sessionId).map { record ->
            UploadFailRecord(
                sessionId = record.sessionId,
                mac = MacAddressCodec.toHex(record.parsedMac),
                result = record.finalStatus,
                reason = record.reason.orEmpty(),
                time = formatIsoTime(record.endedAt)
            )
        }
    }

    override suspend fun getFailRecordsByBatch(batchId: String): List<UploadFailRecord> {
        return testRecordDao.getFailRecordsByBatch(batchId).map { record ->
            UploadFailRecord(
                sessionId = record.sessionId,
                mac = MacAddressCodec.toHex(record.parsedMac),
                result = record.finalStatus,
                reason = record.reason.orEmpty(),
                time = formatIsoTime(record.endedAt)
            )
        }
    }

    override suspend fun getInvalidRecords(sessionId: String): List<UploadInvalidRecord> {
        return testRecordDao.getInvalidRecords(sessionId).map { record ->
            UploadInvalidRecord(
                sessionId = record.sessionId,
                mac = MacAddressCodec.toHex(record.parsedMac),
                time = formatIsoTime(record.endedAt)
            )
        }
    }

    override suspend fun getInvalidRecordsByBatch(batchId: String): List<UploadInvalidRecord> {
        return testRecordDao.getInvalidRecordsByBatch(batchId).map { record ->
            UploadInvalidRecord(
                sessionId = record.sessionId,
                mac = MacAddressCodec.toHex(record.parsedMac),
                time = formatIsoTime(record.endedAt)
            )
        }
    }

    override suspend fun countSuccessBySession(sessionId: String): Int {
        return testRecordDao.countPassBySession(sessionId)
    }

    override suspend fun countFailBySession(sessionId: String): Int {
        return testRecordDao.countFailBySession(sessionId)
    }

    override suspend fun countInvalidBySession(sessionId: String): Int {
        return testRecordDao.countInvalidBySession(sessionId)
    }

    private fun formatIsoTime(timeMillis: Long): String {
        return Instant.ofEpochMilli(timeMillis)
            .atZone(ZoneId.systemDefault())
            .format(isoFormatter)
    }
}
