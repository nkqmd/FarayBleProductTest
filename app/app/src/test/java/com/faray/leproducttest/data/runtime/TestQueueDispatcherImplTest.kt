package com.faray.leproducttest.data.runtime

import com.faray.leproducttest.domain.service.BleTestExecutor
import com.faray.leproducttest.domain.service.ProductionEvent
import com.faray.leproducttest.model.BleTestPlan
import com.faray.leproducttest.model.DeviceUiStatus
import com.faray.leproducttest.model.TestExecutionResult
import com.faray.leproducttest.model.TestTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TestQueueDispatcherImplTest {

    @Test
    fun stopCancelsInFlightTaskAndEmitsAbortWithoutFinishedResult() = runBlocking {
        val events = mutableListOf<ProductionEvent>()
        val dispatcher = TestQueueDispatcherImpl(
            bleTestExecutor = BlockingBleTestExecutor()
        )

        dispatcher.startSession(SESSION_ID, testPlan()) { event ->
            events += event
        }
        dispatcher.offer(testTask())
        delay(100L)

        dispatcher.stop()

        assertTrue(events.any { it == ProductionEvent.ExecutionStatusChanged(MAC, DeviceUiStatus.CONNECTING) })
        assertTrue(events.any { it == ProductionEvent.ExecutionAborted(MAC) })
        assertFalse(events.any { it is ProductionEvent.ExecutionFinished })
    }

    private fun testPlan() = BleTestPlan(
        rssiMin = -80,
        rssiMax = null,
        scanIdleMs = 100L,
        scanActiveMs = 100L,
        maxConcurrent = 1,
        connectTimeoutMs = 10_000L,
        notifyTimeoutMs = 30_000L,
        overallTimeoutMs = 40_000L,
        serviceUuid = "0000180F-0000-1000-8000-00805F9B34FB",
        notifyCharacteristicUuid = "00002A19-0000-1000-8000-00805F9B34FB",
        writeCharacteristicUuid = "00002A19-0000-1000-8000-00805F9B34FB",
        writePayloadHex = "AA",
        expectedNotifyValueHex = "BB"
    )

    private fun testTask() = TestTask(
        sessionId = SESSION_ID,
        batchId = BATCH_ID,
        parsedMac = MAC,
        deviceAddress = ADDRESS,
        advName = ADV_NAME,
        rssi = RSSI,
        createdAt = 1_000L
    )

    private class BlockingBleTestExecutor : BleTestExecutor {
        override suspend fun execute(
            task: TestTask,
            plan: BleTestPlan,
            onStatus: suspend (DeviceUiStatus) -> Unit
        ): TestExecutionResult {
            onStatus(DeviceUiStatus.CONNECTING)
            try {
                delay(60_000L)
            } catch (_: CancellationException) {
                throw CancellationException("stopped")
            }
            error("Expected task cancellation before completion")
        }
    }

    private companion object {
        const val SESSION_ID = "session-1"
        const val BATCH_ID = "batch-1"
        const val MAC = 0x001122AABBCCL
        const val ADDRESS = "AA:BB:CC:DD:EE:FF"
        const val ADV_NAME = "DUT_001122AABBCC"
        const val RSSI = -60
    }
}
