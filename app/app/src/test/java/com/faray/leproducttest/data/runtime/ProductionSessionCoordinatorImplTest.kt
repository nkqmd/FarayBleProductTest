package com.faray.leproducttest.data.runtime

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.faray.leproducttest.ble.scan.ScanScheduler
import com.faray.leproducttest.domain.repository.SessionRepository
import com.faray.leproducttest.domain.service.ProductionEvent
import com.faray.leproducttest.domain.service.ProductionStateMachine
import com.faray.leproducttest.domain.service.RuntimeDeviceStore
import com.faray.leproducttest.domain.service.TestQueueDispatcher
import com.faray.leproducttest.model.BatchProfile
import com.faray.leproducttest.model.BleTestPlan
import com.faray.leproducttest.model.DeviceUiStatus
import com.faray.leproducttest.model.ProductionSession
import com.faray.leproducttest.model.QueueSnapshot
import com.faray.leproducttest.model.RuntimeDeviceItem
import com.faray.leproducttest.model.ScanPayload
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionSessionCoordinatorImplTest {

    @Test
    fun startSessionCoordinatesSubSystems() = runBlocking {
        val callLog = mutableListOf<String>()
        val scanScheduler = FakeScanScheduler()
        val testQueueDispatcher = FakeTestQueueDispatcher()
        val productionStateMachine = FakeProductionStateMachine()
        val runtimeDeviceStore = FakeRuntimeDeviceStore()
        val sessionRepository = FakeSessionRepository()
        scanScheduler.calls = callLog
        testQueueDispatcher.calls = callLog
        productionStateMachine.calls = callLog
        runtimeDeviceStore.calls = callLog
        sessionRepository.calls = callLog
        val coordinator = ProductionSessionCoordinatorImpl(
            scanScheduler = scanScheduler,
            testQueueDispatcher = testQueueDispatcher,
            productionStateMachine = productionStateMachine,
            runtimeDeviceStore = runtimeDeviceStore,
            sessionRepository = sessionRepository
        )

        coordinator.startSession(
            session = testSession(),
            profile = testProfile(),
            staleTimeoutMs = 300L,
            onScanError = { }
        )

        assertEquals(
            listOf("scan.stop", "queue.stop", "machine.stop", "store.clear", "session.create", "machine.start", "queue.start", "scan.start"),
            callLog
        )
        assertEquals("session-1", scanScheduler.startedSessionId)
        assertEquals("session-1", testQueueDispatcher.startedSessionId)
        assertEquals("session-1", productionStateMachine.startedSessionId)
    }

    @Test
    fun stopSessionCoordinatesShutdownAndSessionStopMark() = runBlocking {
        val callLog = mutableListOf<String>()
        val scanScheduler = FakeScanScheduler()
        val testQueueDispatcher = FakeTestQueueDispatcher()
        val productionStateMachine = FakeProductionStateMachine()
        val runtimeDeviceStore = FakeRuntimeDeviceStore()
        val sessionRepository = FakeSessionRepository()
        scanScheduler.calls = callLog
        testQueueDispatcher.calls = callLog
        productionStateMachine.calls = callLog
        sessionRepository.calls = callLog
        val coordinator = ProductionSessionCoordinatorImpl(
            scanScheduler = scanScheduler,
            testQueueDispatcher = testQueueDispatcher,
            productionStateMachine = productionStateMachine,
            runtimeDeviceStore = runtimeDeviceStore,
            sessionRepository = sessionRepository
        )

        coordinator.stopSession("session-1", 2_000L)

        assertEquals(
            listOf("scan.stop", "queue.stop", "machine.stop", "session.stop"),
            callLog
        )
        assertEquals("session-1", sessionRepository.stoppedSessionId)
    }

    @Test
    fun scanPayloadAndTickAreRoutedIntoStateMachineEvents() = runBlocking {
        val scanScheduler = FakeScanScheduler()
        val productionStateMachine = FakeProductionStateMachine()
        val coordinator = ProductionSessionCoordinatorImpl(
            scanScheduler = scanScheduler,
            testQueueDispatcher = FakeTestQueueDispatcher(),
            productionStateMachine = productionStateMachine,
            runtimeDeviceStore = FakeRuntimeDeviceStore(),
            sessionRepository = FakeSessionRepository()
        )

        coordinator.startSession(
            session = testSession(),
            profile = testProfile(),
            staleTimeoutMs = 300L,
            onScanError = { }
        )

        scanScheduler.onPayload?.invoke(
            ScanPayload(
                deviceAddress = "AA:BB:CC:DD:EE:FF",
                advName = "DUT_001122AABBCC",
                rssi = -60,
                seenAt = 1_000L
            )
        )
        scanScheduler.onCycle?.invoke(1_234L)

        assertTrue(productionStateMachine.dispatchedEvents.any { it is ProductionEvent.ScanSeen })
        assertTrue(productionStateMachine.dispatchedEvents.any { it == ProductionEvent.CleanupTick(1_234L) })
    }

    private fun testSession() = ProductionSession(
        sessionId = "session-1",
        batchId = "batch-1",
        factoryId = "factory-1",
        startedAt = 1_000L,
        status = com.faray.leproducttest.model.SessionStatus.RUNNING
    )

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

    private class FakeScanScheduler : ScanScheduler {
        var calls = mutableListOf<String>()
        var startedSessionId: String? = null
        var onPayload: (suspend (ScanPayload) -> Unit)? = null
        var onCycle: (suspend (Long) -> Unit)? = null

        override suspend fun start(
            sessionId: String,
            plan: BleTestPlan,
            onPayload: suspend (ScanPayload) -> Unit,
            onCycle: suspend (Long) -> Unit,
            onError: suspend (Int) -> Unit
        ) {
            calls += "scan.start"
            startedSessionId = sessionId
            this.onPayload = onPayload
            this.onCycle = onCycle
        }

        override suspend fun stop() {
            calls += "scan.stop"
        }

        override fun isRunning(): Boolean = false
    }

    private class FakeTestQueueDispatcher : TestQueueDispatcher {
        var calls = mutableListOf<String>()
        var startedSessionId: String? = null

        override suspend fun startSession(
            sessionId: String,
            plan: BleTestPlan,
            onEvent: suspend (ProductionEvent) -> Unit
        ) {
            calls += "queue.start"
            startedSessionId = sessionId
        }

        override suspend fun offer(task: com.faray.leproducttest.model.TestTask): Boolean = true

        override suspend fun stop() {
            calls += "queue.stop"
        }
    }

    private class FakeProductionStateMachine : ProductionStateMachine {
        var calls = mutableListOf<String>()
        val dispatchedEvents = mutableListOf<ProductionEvent>()
        var startedSessionId: String? = null

        override suspend fun startSession(sessionId: String, profile: BatchProfile, staleAfterMs: Long) {
            calls += "machine.start"
            startedSessionId = sessionId
        }

        override suspend fun dispatch(event: ProductionEvent) {
            dispatchedEvents += event
        }

        override suspend fun stopSession() {
            calls += "machine.stop"
        }

        override fun observeQueueState(): LiveData<QueueSnapshot> = MutableLiveData(QueueSnapshot(0, 0, 1))
    }

    private class FakeRuntimeDeviceStore : RuntimeDeviceStore {
        var calls = mutableListOf<String>()

        override suspend fun save(item: RuntimeDeviceItem): RuntimeDeviceItem = item

        override suspend fun find(parsedMac: Long): RuntimeDeviceItem? = null

        override suspend fun remove(parsedMac: Long): RuntimeDeviceItem? = null

        override suspend fun clear() {
            calls += "store.clear"
        }

        override suspend fun snapshot(): List<RuntimeDeviceItem> = emptyList()

        override fun observeVisibleDevices(): LiveData<List<RuntimeDeviceItem>> = MutableLiveData(emptyList())
    }

    private class FakeSessionRepository : SessionRepository {
        var calls = mutableListOf<String>()
        var stoppedSessionId: String? = null

        override suspend fun createSession(session: ProductionSession): ProductionSession {
            calls += "session.create"
            return session
        }

        override suspend fun markSessionStopped(sessionId: String, endedAt: Long) {
            calls += "session.stop"
            stoppedSessionId = sessionId
        }

        override suspend fun markSessionUploaded(sessionId: String) = Unit

        override suspend fun getSession(sessionId: String): ProductionSession? = null

        override suspend fun getRunningSession(): ProductionSession? = null

        override suspend fun getLatestFinishedSession(batchId: String): ProductionSession? = null

        override suspend fun getLatestStoppedSession(batchId: String): ProductionSession? = null
    }
}
