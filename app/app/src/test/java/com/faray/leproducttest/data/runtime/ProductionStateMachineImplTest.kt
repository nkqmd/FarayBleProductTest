package com.faray.leproducttest.data.runtime

import com.faray.leproducttest.ble.parser.AdvertisementParser
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.faray.leproducttest.domain.repository.BatchRepository
import com.faray.leproducttest.domain.repository.TestRecordRepository
import com.faray.leproducttest.domain.service.ProductionEvent
import com.faray.leproducttest.domain.service.RuntimeDeviceStore
import com.faray.leproducttest.domain.service.TestQueueDispatcher
import com.faray.leproducttest.model.BatchProfile
import com.faray.leproducttest.model.BleTestPlan
import com.faray.leproducttest.model.DeviceUiStatus
import com.faray.leproducttest.model.RuntimeDeviceItem
import com.faray.leproducttest.model.ScanPayload
import com.faray.leproducttest.model.SessionStatistics
import com.faray.leproducttest.model.TestExecutionResult
import com.faray.leproducttest.model.TestTask
import com.faray.leproducttest.model.UploadFailRecord
import com.faray.leproducttest.model.UploadInvalidRecord
import com.faray.leproducttest.model.UploadSuccessRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionStateMachineImplTest {

    @Test
    fun validDeviceTransitionsToQueued() = runBlocking {
        val runtimeDeviceStore = FakeRuntimeDeviceStore()
        val stateMachine = ProductionStateMachineImpl(
            runtimeDeviceStore = runtimeDeviceStore,
            batchRepository = FakeBatchRepository(whitelisted = true),
            advertisementParser = AdvertisementParser(),
            testRecordRepository = FakeTestRecordRepository(hasFinalRecord = false),
            testQueueDispatcher = FakeTestQueueDispatcher()
        )

        stateMachine.startSession(
            sessionId = "session-1",
            profile = testProfile(),
            staleAfterMs = 300L
        )
        stateMachine.dispatch(
            ProductionEvent.ScanSeen(
                ScanPayload(
                    deviceAddress = "AA:BB:CC:DD:EE:FF",
                    advName = "DUT_001122AABBCC",
                    rssi = -60,
                    seenAt = 1_000L
                )
            )
        )

        val device = runtimeDeviceStore.find(0x001122AABBCCL)
        assertNotNull(device)
        assertEquals(DeviceUiStatus.QUEUED, device?.uiStatus)
    }

    @Test
    fun cleanupTickUsesSessionStaleTimeout() = runBlocking {
        val runtimeDeviceStore = FakeRuntimeDeviceStore()
        runtimeDeviceStore.save(
            RuntimeDeviceItem(
                parsedMac = 0x001122AABBCCL,
                deviceAddress = "AA:BB:CC:DD:EE:FF",
                advName = "DUT_001122AABBCC",
                rssi = -60,
                lastSeenAt = 900L,
                sequenceNo = 1L,
                uiStatus = DeviceUiStatus.QUEUED
            )
        )
        val stateMachine = ProductionStateMachineImpl(
            runtimeDeviceStore = runtimeDeviceStore,
            batchRepository = FakeBatchRepository(whitelisted = true),
            advertisementParser = AdvertisementParser(),
            testRecordRepository = FakeTestRecordRepository(hasFinalRecord = false),
            testQueueDispatcher = FakeTestQueueDispatcher()
        )

        stateMachine.startSession(
            sessionId = "session-1",
            profile = testProfile(),
            staleAfterMs = 300L
        )
        stateMachine.dispatch(ProductionEvent.CleanupTick(now = 1_234L))

        assertNull(runtimeDeviceStore.find(0x001122AABBCCL))
    }

    @Test
    fun eventsAreIgnoredAfterStop() = runBlocking {
        val runtimeDeviceStore = FakeRuntimeDeviceStore()
        val stateMachine = ProductionStateMachineImpl(
            runtimeDeviceStore = runtimeDeviceStore,
            batchRepository = FakeBatchRepository(whitelisted = true),
            advertisementParser = AdvertisementParser(),
            testRecordRepository = FakeTestRecordRepository(hasFinalRecord = false),
            testQueueDispatcher = FakeTestQueueDispatcher()
        )

        stateMachine.startSession(
            sessionId = "session-1",
            profile = testProfile(),
            staleAfterMs = 300L
        )
        stateMachine.stopSession()
        stateMachine.dispatch(
            ProductionEvent.ScanSeen(
                ScanPayload(
                    deviceAddress = "AA:BB:CC:DD:EE:FF",
                    advName = "DUT_001122AABBCC",
                    rssi = -60,
                    seenAt = 1_000L
                )
            )
        )
        stateMachine.dispatch(ProductionEvent.CleanupTick(now = 1_234L))

        assertNull(runtimeDeviceStore.find(0x001122AABBCCL))
        assertTrue(runtimeDeviceStore.snapshot().isEmpty())
    }

    @Test
    fun invalidDeviceIsRecordedOnceAndThenRefreshedAsSticky() = runBlocking {
        val runtimeDeviceStore = FakeRuntimeDeviceStore()
        val testRecordRepository = FakeTestRecordRepository(hasFinalRecord = false)
        val stateMachine = ProductionStateMachineImpl(
            runtimeDeviceStore = runtimeDeviceStore,
            batchRepository = FakeBatchRepository(whitelisted = false),
            advertisementParser = AdvertisementParser(),
            testRecordRepository = testRecordRepository,
            testQueueDispatcher = FakeTestQueueDispatcher()
        )

        stateMachine.startSession("session-1", testProfile(), 300L)
        stateMachine.dispatch(scanSeen("DUT_001122AABBCC", 1_000L))
        stateMachine.dispatch(scanSeen("DUT_001122AABBCC", 1_500L))

        val device = runtimeDeviceStore.find(0x001122AABBCCL)
        assertNotNull(device)
        assertEquals(DeviceUiStatus.INVALID_DEVICE, device?.uiStatus)
        assertEquals(1_500L, device?.lastSeenAt)
        assertEquals(1, testRecordRepository.savedResults.size)
        assertEquals(DeviceUiStatus.INVALID_DEVICE, testRecordRepository.savedResults.single().finalStatus)
    }

    @Test
    fun deviceWithFinalRecordBecomesAlreadyTested() = runBlocking {
        val runtimeDeviceStore = FakeRuntimeDeviceStore()
        val stateMachine = ProductionStateMachineImpl(
            runtimeDeviceStore = runtimeDeviceStore,
            batchRepository = FakeBatchRepository(whitelisted = true),
            advertisementParser = AdvertisementParser(),
            testRecordRepository = FakeTestRecordRepository(hasFinalRecord = true),
            testQueueDispatcher = FakeTestQueueDispatcher()
        )

        stateMachine.startSession("session-1", testProfile(), 300L)
        stateMachine.dispatch(scanSeen("DUT_001122AABBCC", 1_000L))

        val device = runtimeDeviceStore.find(0x001122AABBCCL)
        assertNotNull(device)
        assertEquals(DeviceUiStatus.ALREADY_TESTED, device?.uiStatus)
    }

    @Test
    fun lowercaseMacSuffixIsIgnored() = runBlocking {
        val runtimeDeviceStore = FakeRuntimeDeviceStore()
        val queueDispatcher = FakeTestQueueDispatcher()
        val stateMachine = ProductionStateMachineImpl(
            runtimeDeviceStore = runtimeDeviceStore,
            batchRepository = FakeBatchRepository(whitelisted = true),
            advertisementParser = AdvertisementParser(),
            testRecordRepository = FakeTestRecordRepository(hasFinalRecord = false),
            testQueueDispatcher = queueDispatcher
        )

        stateMachine.startSession("session-1", testProfile(), 300L)
        stateMachine.dispatch(scanSeen("DUT_001122aabbcc", 1_000L))

        assertNull(runtimeDeviceStore.find(0x001122AABBCCL))
        assertFalse(queueDispatcher.offered)
    }

    @Test
    fun passDeviceVisibleForMoreThanOneMinuteBecomesAlreadyTested() = runBlocking {
        val runtimeDeviceStore = FakeRuntimeDeviceStore()
        runtimeDeviceStore.save(
            RuntimeDeviceItem(
                parsedMac = 0x001122AABBCCL,
                deviceAddress = "AA:BB:CC:DD:EE:FF",
                advName = "DUT_001122AABBCC",
                rssi = -60,
                lastSeenAt = 1_000L,
                sequenceNo = 1L,
                uiStatus = DeviceUiStatus.PASS,
                passAt = 1_000L
            )
        )
        val stateMachine = ProductionStateMachineImpl(
            runtimeDeviceStore = runtimeDeviceStore,
            batchRepository = FakeBatchRepository(whitelisted = true),
            advertisementParser = AdvertisementParser(),
            testRecordRepository = FakeTestRecordRepository(hasFinalRecord = false),
            testQueueDispatcher = FakeTestQueueDispatcher()
        )

        stateMachine.startSession("session-1", testProfile(), 300L)
        stateMachine.dispatch(scanSeen("DUT_001122AABBCC", 61_001L))

        val device = runtimeDeviceStore.find(0x001122AABBCCL)
        assertNotNull(device)
        assertEquals(DeviceUiStatus.ALREADY_TESTED, device?.uiStatus)
    }

    @Test
    fun executionStatusEventUpdatesRuntimeStatus() = runBlocking {
        val runtimeDeviceStore = FakeRuntimeDeviceStore()
        runtimeDeviceStore.save(
            RuntimeDeviceItem(
                parsedMac = 0x001122AABBCCL,
                deviceAddress = "AA:BB:CC:DD:EE:FF",
                advName = "DUT_001122AABBCC",
                rssi = -60,
                lastSeenAt = 1_000L,
                sequenceNo = 1L,
                uiStatus = DeviceUiStatus.QUEUED
            )
        )
        val stateMachine = ProductionStateMachineImpl(
            runtimeDeviceStore = runtimeDeviceStore,
            batchRepository = FakeBatchRepository(whitelisted = true),
            advertisementParser = AdvertisementParser(),
            testRecordRepository = FakeTestRecordRepository(hasFinalRecord = false),
            testQueueDispatcher = FakeTestQueueDispatcher()
        )

        stateMachine.startSession("session-1", testProfile(), 300L)
        stateMachine.dispatch(
            ProductionEvent.ExecutionStatusChanged(
                parsedMac = 0x001122AABBCCL,
                status = DeviceUiStatus.CONNECTING
            )
        )

        val device = runtimeDeviceStore.find(0x001122AABBCCL)
        assertNotNull(device)
        assertEquals(DeviceUiStatus.CONNECTING, device?.uiStatus)
    }

    @Test
    fun executionFinishedEventPersistsResultAndMarksTerminal() = runBlocking {
        val runtimeDeviceStore = FakeRuntimeDeviceStore()
        runtimeDeviceStore.save(
            RuntimeDeviceItem(
                parsedMac = 0x001122AABBCCL,
                deviceAddress = "AA:BB:CC:DD:EE:FF",
                advName = "DUT_001122AABBCC",
                rssi = -60,
                lastSeenAt = 1_000L,
                sequenceNo = 1L,
                uiStatus = DeviceUiStatus.CONNECTING
            )
        )
        val testRecordRepository = FakeTestRecordRepository(hasFinalRecord = false)
        val stateMachine = ProductionStateMachineImpl(
            runtimeDeviceStore = runtimeDeviceStore,
            batchRepository = FakeBatchRepository(whitelisted = true),
            advertisementParser = AdvertisementParser(),
            testRecordRepository = testRecordRepository,
            testQueueDispatcher = FakeTestQueueDispatcher()
        )

        stateMachine.startSession("session-1", testProfile(), 300L)
        stateMachine.dispatch(
            ProductionEvent.ExecutionFinished(
                TestExecutionResult(
                    sessionId = "session-1",
                    batchId = "batch-1",
                    parsedMac = 0x001122AABBCCL,
                    deviceAddress = "AA:BB:CC:DD:EE:FF",
                    advName = "DUT_001122AABBCC",
                    rssi = -60,
                    finalStatus = DeviceUiStatus.PASS,
                    success = true,
                    reason = null,
                    failureReason = null,
                    startedAt = 1_000L,
                    endedAt = 2_000L
                )
            )
        )

        val device = runtimeDeviceStore.find(0x001122AABBCCL)
        assertNotNull(device)
        assertEquals(DeviceUiStatus.PASS, device?.uiStatus)
        assertEquals(1, testRecordRepository.savedResults.size)
        assertEquals(DeviceUiStatus.PASS, testRecordRepository.savedResults.single().finalStatus)
    }

    @Test
    fun executionAbortedEventRemovesRuntimeDevice() = runBlocking {
        val runtimeDeviceStore = FakeRuntimeDeviceStore()
        runtimeDeviceStore.save(
            RuntimeDeviceItem(
                parsedMac = 0x001122AABBCCL,
                deviceAddress = "AA:BB:CC:DD:EE:FF",
                advName = "DUT_001122AABBCC",
                rssi = -60,
                lastSeenAt = 1_000L,
                sequenceNo = 1L,
                uiStatus = DeviceUiStatus.CONNECTING
            )
        )
        val stateMachine = ProductionStateMachineImpl(
            runtimeDeviceStore = runtimeDeviceStore,
            batchRepository = FakeBatchRepository(whitelisted = true),
            advertisementParser = AdvertisementParser(),
            testRecordRepository = FakeTestRecordRepository(hasFinalRecord = false),
            testQueueDispatcher = FakeTestQueueDispatcher()
        )

        stateMachine.startSession("session-1", testProfile(), 300L)
        stateMachine.dispatch(ProductionEvent.ExecutionAborted(parsedMac = 0x001122AABBCCL))

        assertNull(runtimeDeviceStore.find(0x001122AABBCCL))
    }

    @Test
    fun queueStateIsPublishedByActorAcrossQueuedRunningAndFinished() = runBlocking {
        val runtimeDeviceStore = FakeRuntimeDeviceStore()
        val stateMachine = ProductionStateMachineImpl(
            runtimeDeviceStore = runtimeDeviceStore,
            batchRepository = FakeBatchRepository(whitelisted = true),
            advertisementParser = AdvertisementParser(),
            testRecordRepository = FakeTestRecordRepository(hasFinalRecord = false),
            testQueueDispatcher = FakeTestQueueDispatcher()
        )

        stateMachine.startSession("session-1", testProfile(), 300L)
        assertEquals(0, stateMachine.observeQueueState().value?.queuedCount)
        assertEquals(0, stateMachine.observeQueueState().value?.runningCount)
        assertEquals(1, stateMachine.observeQueueState().value?.maxConcurrent)

        stateMachine.dispatch(scanSeen("DUT_001122AABBCC", 1_000L))
        assertEquals(1, stateMachine.observeQueueState().value?.queuedCount)
        assertEquals(0, stateMachine.observeQueueState().value?.runningCount)

        stateMachine.dispatch(
            ProductionEvent.ExecutionStatusChanged(
                parsedMac = 0x001122AABBCCL,
                status = DeviceUiStatus.CONNECTING
            )
        )
        assertEquals(0, stateMachine.observeQueueState().value?.queuedCount)
        assertEquals(1, stateMachine.observeQueueState().value?.runningCount)

        stateMachine.dispatch(
            ProductionEvent.ExecutionFinished(
                TestExecutionResult(
                    sessionId = "session-1",
                    batchId = "batch-1",
                    parsedMac = 0x001122AABBCCL,
                    deviceAddress = "AA:BB:CC:DD:EE:FF",
                    advName = "DUT_001122AABBCC",
                    rssi = -60,
                    finalStatus = DeviceUiStatus.PASS,
                    success = true,
                    reason = null,
                    failureReason = null,
                    startedAt = 1_000L,
                    endedAt = 2_000L
                )
            )
        )
        assertEquals(0, stateMachine.observeQueueState().value?.queuedCount)
        assertEquals(0, stateMachine.observeQueueState().value?.runningCount)
    }

    private fun testProfile() = BatchProfile(
        batchId = "batch-1",
        expectedCount = 100,
        expireTime = "2026-12-31 23:59:59",
        expectedFirmware = "1.0.0",
        deviceType = "DUT",
        bleNamePrefix = "DUT_",
        bleConfig = BleTestPlan(
            rssiMin = -80,
            rssiMax = null,
            scanIdleMs = 100L,
            scanActiveMs = 100L,
            maxConcurrent = 1,
            connectTimeoutMs = 10_000L,
            notifyTimeoutMs = 30_000L,
            overallTimeoutMs = 40_000L,
            serviceUuid = "service",
            notifyCharacteristicUuid = "notify",
            writeCharacteristicUuid = "write",
            writePayloadHex = "AA",
            expectedNotifyValueHex = "BB"
        ),
        rawBleConfigJson = "{}",
        macListCount = 100,
        macListHash = "hash",
        macListVersion = "v1",
        macListUrl = "/mac-list"
    )

    private fun scanSeen(advName: String, seenAt: Long) = ProductionEvent.ScanSeen(
        ScanPayload(
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            advName = advName,
            rssi = -60,
            seenAt = seenAt
        )
    )

    private class FakeRuntimeDeviceStore : RuntimeDeviceStore {
        private val devices = linkedMapOf<Long, RuntimeDeviceItem>()
        private val visibleDevices = MutableLiveData<List<RuntimeDeviceItem>>(emptyList())

        override suspend fun save(item: RuntimeDeviceItem): RuntimeDeviceItem {
            devices[item.parsedMac] = item
            visibleDevices.postValue(devices.values.toList())
            return item
        }

        override suspend fun find(parsedMac: Long): RuntimeDeviceItem? = devices[parsedMac]

        override suspend fun remove(parsedMac: Long): RuntimeDeviceItem? {
            val removed = devices.remove(parsedMac)
            visibleDevices.postValue(devices.values.toList())
            return removed
        }

        override suspend fun clear() {
            devices.clear()
            visibleDevices.postValue(emptyList())
        }

        override suspend fun snapshot(): List<RuntimeDeviceItem> = devices.values.toList()

        override fun observeVisibleDevices(): LiveData<List<RuntimeDeviceItem>> = visibleDevices
    }

    private class FakeBatchRepository(
        private val whitelisted: Boolean
    ) : BatchRepository {
        override suspend fun fetchAndSaveBatchSummary(accessToken: String, batchId: String) =
            throw UnsupportedOperationException()

        override suspend fun downloadAndImportMacList(accessToken: String, batchId: String) =
            throw UnsupportedOperationException()

        override suspend fun getBatchProfile(batchId: String): BatchProfile? = null

        override suspend fun countImportedMacs(batchId: String): Int = 0

        override suspend fun isMacWhitelisted(batchId: String, macValue: Long): Boolean = whitelisted
    }

    private class FakeTestRecordRepository(
        private val hasFinalRecord: Boolean
    ) : TestRecordRepository {
        val savedResults = mutableListOf<TestExecutionResult>()

        override suspend fun saveExecutionResult(result: TestExecutionResult) {
            savedResults += result
        }

        override suspend fun hasFinalRecord(batchId: String, parsedMac: Long): Boolean = hasFinalRecord

        override suspend fun getSessionStatistics(sessionId: String): SessionStatistics = SessionStatistics(0, 0, 0, 0, 0.0)

        override suspend fun getSuccessRecords(sessionId: String): List<UploadSuccessRecord> = emptyList()

        override suspend fun getFailRecords(sessionId: String): List<UploadFailRecord> = emptyList()

        override suspend fun getInvalidRecords(sessionId: String): List<UploadInvalidRecord> = emptyList()

        override suspend fun countSuccessBySession(sessionId: String): Int = 0

        override suspend fun countFailBySession(sessionId: String): Int = 0

        override suspend fun countInvalidBySession(sessionId: String): Int = 0
    }

    private class FakeTestQueueDispatcher : TestQueueDispatcher {
        var offered = false

        override suspend fun startSession(
            sessionId: String,
            plan: BleTestPlan,
            onEvent: suspend (ProductionEvent) -> Unit
        ) = Unit

        override suspend fun offer(task: TestTask): Boolean {
            offered = true
            return true
        }

        override suspend fun stop() = Unit
    }
}
