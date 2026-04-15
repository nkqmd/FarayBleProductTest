package com.faray.leproducttest.domain.usecase

import com.faray.leproducttest.domain.repository.BatchRepository
import com.faray.leproducttest.domain.repository.LocalConfigRepository
import com.faray.leproducttest.domain.repository.ResultUploadRepository
import com.faray.leproducttest.domain.repository.SessionRepository
import com.faray.leproducttest.domain.repository.TestRecordRepository
import com.faray.leproducttest.model.BatchProfile
import com.faray.leproducttest.model.BleTestPlan
import com.faray.leproducttest.model.ProductionSession
import com.faray.leproducttest.model.SessionStatistics
import com.faray.leproducttest.model.SessionStatus
import com.faray.leproducttest.model.TestExecutionResult
import com.faray.leproducttest.model.UploadFailRecord
import com.faray.leproducttest.model.UploadInvalidRecord
import com.faray.leproducttest.model.UploadProductionResultRequest
import com.faray.leproducttest.model.UploadProductionResultResponse
import com.faray.leproducttest.model.UploadRecord
import com.faray.leproducttest.model.UploadSuccessRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreResultStateUseCaseTest {

    @Test
    fun restoreLatestFinishedSessionWithLatestUploadRecord() = kotlinx.coroutines.runBlocking {
        val useCase = RestoreResultStateUseCase(
            localConfigRepository = FakeLocalConfigRepository(
                factoryId = "F01",
                batchId = "Aa"
            ),
            batchRepository = FakeBatchRepository(
                BatchProfile(
                    batchId = "Aa",
                    expectedCount = 100,
                    expireTime = "2026-12-31 23:59:59",
                    expectedFirmware = "1.0.0",
                    deviceType = "DUT",
                    bleNamePrefix = "DUT_",
                    bleConfig = BleTestPlan(
                        rssiMin = -80,
                        rssiMax = null,
                        scanIdleMs = 100,
                        scanActiveMs = 100,
                        maxConcurrent = 1,
                        connectTimeoutMs = 10_000,
                        notifyTimeoutMs = 30_000,
                        overallTimeoutMs = 40_000,
                        serviceUuid = "service",
                        notifyCharacteristicUuid = "notify",
                        writeCharacteristicUuid = "write",
                        writePayloadHex = "AA",
                        expectedNotifyValueHex = "BB"
                    ),
                    rawBleConfigJson = "{}",
                    macListCount = 4,
                    macListHash = "hash",
                    macListVersion = "v1",
                    macListUrl = "/mac-list"
                )
            ),
            sessionRepository = FakeSessionRepository(
                ProductionSession(
                    sessionId = "SESSION_1",
                    batchId = "Aa",
                    factoryId = "F01",
                    startedAt = 1_000,
                    endedAt = 2_000,
                    status = SessionStatus.UPLOADED
                )
            ),
            testRecordRepository = FakeTestRecordRepository(
                SessionStatistics(
                    actualCount = 2,
                    successCount = 1,
                    failCount = 1,
                    invalidCount = 1,
                    successRate = 50.0
                )
            ),
            resultUploadRepository = FakeResultUploadRepository(
                UploadRecord(
                    sessionId = "SESSION_1",
                    batchId = "Aa",
                    reportId = "Aa_deadbeefdeadbeef",
                    reportDigest = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef",
                    uploadId = "UPLOAD001",
                    duplicate = false,
                    uploadedAt = 3_000,
                    message = "upload success"
                )
            )
        )

        val restored = useCase()

        assertEquals("SESSION_1", restored.resultState.sessionId)
        assertEquals("Aa", restored.resultState.batchId)
        assertEquals(100, restored.resultState.expectedCount)
        assertEquals(2, restored.resultState.actualCount)
        assertEquals(1, restored.resultState.successCount)
        assertEquals(1, restored.resultState.failCount)
        assertEquals(1, restored.resultState.invalidCount)
        assertEquals("UPLOAD001", restored.resultState.uploadId)
        assertEquals("Upload success", restored.resultState.uploadStatus)
        assertTrue(restored.resultState.uploadEnabled)
        assertFalse(restored.resultState.duplicate)
        assertEquals("SESSION_1", restored.shellState.activeSessionId)
    }

    @Test
    fun recoverInterruptedRunningSessionAsStoppedResult() = kotlinx.coroutines.runBlocking {
        val sessionRepository = FakeSessionRepository(
            session = ProductionSession(
                sessionId = "SESSION_RUNNING",
                batchId = "Bb",
                factoryId = "F02",
                startedAt = 2_000,
                endedAt = null,
                status = SessionStatus.RUNNING
            )
        )
        val useCase = RestoreResultStateUseCase(
            localConfigRepository = FakeLocalConfigRepository(
                factoryId = "F02",
                batchId = "Bb"
            ),
            batchRepository = FakeBatchRepository(
                BatchProfile(
                    batchId = "Bb",
                    expectedCount = 20,
                    expireTime = "2026-12-31 23:59:59",
                    expectedFirmware = "1.0.0",
                    deviceType = "DUT",
                    bleNamePrefix = "DUT_",
                    bleConfig = BleTestPlan(
                        rssiMin = -80,
                        rssiMax = null,
                        scanIdleMs = 100,
                        scanActiveMs = 100,
                        maxConcurrent = 1,
                        connectTimeoutMs = 10_000,
                        notifyTimeoutMs = 30_000,
                        overallTimeoutMs = 40_000,
                        serviceUuid = "service",
                        notifyCharacteristicUuid = "notify",
                        writeCharacteristicUuid = "write",
                        writePayloadHex = "AA",
                        expectedNotifyValueHex = "BB"
                    ),
                    rawBleConfigJson = "{}",
                    macListCount = 4,
                    macListHash = "hash",
                    macListVersion = "v1",
                    macListUrl = "/mac-list"
                )
            ),
            sessionRepository = sessionRepository,
            testRecordRepository = FakeTestRecordRepository(
                SessionStatistics(
                    actualCount = 1,
                    successCount = 1,
                    failCount = 0,
                    invalidCount = 0,
                    successRate = 100.0
                )
            ),
            resultUploadRepository = FakeResultUploadRepository(null)
        )

        val restored = useCase()

        assertEquals("SESSION_RUNNING", restored.resultState.sessionId)
        assertEquals("Bb", restored.resultState.batchId)
        assertEquals("Pending upload", restored.resultState.uploadStatus)
        assertEquals("Recovered the interrupted session after app restart", restored.resultState.uploadMessage)
        assertTrue(restored.resultState.uploadEnabled)
        assertEquals("SESSION_RUNNING", restored.shellState.activeSessionId)
        assertEquals("SESSION_RUNNING", sessionRepository.stoppedSessionId)
        assertTrue((sessionRepository.stoppedAt ?: 0L) >= 2_000L)
    }

    private class FakeLocalConfigRepository(
        private val factoryId: String,
        private val batchId: String
    ) : LocalConfigRepository {
        override suspend fun saveFactoryId(factoryId: String) = Unit
        override suspend fun getFactoryId(): String? = factoryId
        override suspend fun saveLastBatchId(batchId: String) = Unit
        override suspend fun getLastBatchId(): String? = batchId
    }

    private class FakeBatchRepository(
        private val batchProfile: BatchProfile
    ) : BatchRepository {
        override suspend fun fetchAndSaveBatchSummary(accessToken: String, batchId: String): Result<BatchProfile> {
            throw UnsupportedOperationException()
        }

        override suspend fun downloadAndImportMacList(accessToken: String, batchId: String) =
            throw UnsupportedOperationException()

        override suspend fun getBatchProfile(batchId: String): BatchProfile? = batchProfile
        override suspend fun countImportedMacs(batchId: String): Int = 0
        override suspend fun isMacWhitelisted(batchId: String, macValue: Long): Boolean = false
    }

    private class FakeSessionRepository(
        private val session: ProductionSession
    ) : SessionRepository {
        var stoppedSessionId: String? = null
        var stoppedAt: Long? = null

        override suspend fun createSession(session: ProductionSession): ProductionSession = session
        override suspend fun markSessionStopped(sessionId: String, endedAt: Long) {
            stoppedSessionId = sessionId
            stoppedAt = endedAt
        }
        override suspend fun markSessionUploaded(sessionId: String) = Unit
        override suspend fun getSession(sessionId: String): ProductionSession? = if (session.sessionId == sessionId) session else null
        override suspend fun getRunningSession(): ProductionSession? = if (session.status == SessionStatus.RUNNING) session else null
        override suspend fun getLatestFinishedSession(batchId: String): ProductionSession? =
            if (session.status == SessionStatus.RUNNING) null else session
        override suspend fun getLatestStoppedSession(batchId: String): ProductionSession? = session
    }

    private class FakeTestRecordRepository(
        private val statistics: SessionStatistics
    ) : TestRecordRepository {
        override suspend fun saveExecutionResult(result: TestExecutionResult) = Unit
        override suspend fun hasFinalRecord(batchId: String, parsedMac: Long): Boolean = false
        override suspend fun getSessionStatistics(sessionId: String): SessionStatistics = statistics
        override suspend fun getSuccessRecords(sessionId: String): List<UploadSuccessRecord> = emptyList()
        override suspend fun getFailRecords(sessionId: String): List<UploadFailRecord> = emptyList()
        override suspend fun getInvalidRecords(sessionId: String): List<UploadInvalidRecord> = emptyList()
        override suspend fun countSuccessBySession(sessionId: String): Int = statistics.successCount
        override suspend fun countFailBySession(sessionId: String): Int = statistics.failCount
        override suspend fun countInvalidBySession(sessionId: String): Int = statistics.invalidCount
    }

    private class FakeResultUploadRepository(
        private val uploadRecord: UploadRecord?
    ) : ResultUploadRepository {
        override suspend fun upload(
            accessToken: String,
            request: UploadProductionResultRequest
        ): Result<UploadProductionResultResponse> {
            throw UnsupportedOperationException()
        }

        override suspend fun uploadBatch(
            accessToken: String,
            request: com.faray.leproducttest.model.UploadBatchResultRequest
        ): Result<com.faray.leproducttest.model.UploadBatchResultResponse> {
            throw UnsupportedOperationException()
        }

        override suspend fun saveUploadRecord(record: UploadRecord) = Unit

        override suspend fun saveBatchUploadRecord(record: UploadRecord) = Unit

        override suspend fun getLatestUploadRecord(sessionId: String): UploadRecord? = uploadRecord

        override suspend fun getLatestBatchUploadRecord(batchId: String): UploadRecord? = null
    }
}
