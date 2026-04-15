package com.faray.leproducttest.data.runtime

import android.util.Log
import com.faray.leproducttest.domain.service.BleTestExecutor
import com.faray.leproducttest.domain.service.ProductionEvent
import com.faray.leproducttest.domain.service.TestQueueDispatcher
import com.faray.leproducttest.model.BleFailureReason
import com.faray.leproducttest.model.BleTestPlan
import com.faray.leproducttest.model.DeviceUiStatus
import com.faray.leproducttest.model.TestExecutionResult
import com.faray.leproducttest.model.TestTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock

class TestQueueDispatcherImpl(
    private val bleTestExecutor: BleTestExecutor
) : TestQueueDispatcher {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private var activeSessionId: String? = null
    private var activePlan: BleTestPlan? = null
    private var activeSessionJob: Job? = null
    private var activeSemaphore: Semaphore = Semaphore(1)
    private var activeEventSink: (suspend (ProductionEvent) -> Unit)? = null

    override suspend fun startSession(
        sessionId: String,
        plan: BleTestPlan,
        onEvent: suspend (ProductionEvent) -> Unit
    ) {
        stop()
        mutex.withLock {
            activeSessionId = sessionId
            activePlan = plan
            activeSessionJob = SupervisorJob(scope.coroutineContext[Job])
            activeSemaphore = Semaphore(plan.maxConcurrent.coerceAtLeast(1))
            activeEventSink = onEvent
        }
        Log.d(TAG, "session:start session=$sessionId maxConcurrent=${plan.maxConcurrent}")
    }

    override suspend fun offer(task: TestTask): Boolean {
        val sessionJob: Job
        val plan: BleTestPlan
        val eventSink: suspend (ProductionEvent) -> Unit
        mutex.withLock {
            if (task.sessionId != activeSessionId) {
                Log.w(TAG, "queue:reject session-mismatch taskSession=${task.sessionId} activeSession=$activeSessionId mac=${task.parsedMac.toString(16).uppercase()}")
                return false
            }
            plan = activePlan ?: return false
            sessionJob = activeSessionJob ?: return false
            eventSink = activeEventSink ?: return false
        }
        Log.d(TAG, "queue:offer mac=${task.parsedMac.toString(16).uppercase()} addr=${task.deviceAddress} adv=${task.advName}")

        scope.launch(sessionJob) {
            runTask(task = task, plan = plan, eventSink = eventSink)
        }
        return true
    }

    override suspend fun stop() {
        val sessionJob = mutex.withLock {
            val current = activeSessionJob
            activeSessionJob = null
            activeSessionId = null
            activePlan = null
            activeEventSink = null
            current
        }
        Log.d(TAG, "session:stop")
        sessionJob?.cancelAndJoin()
    }

    private suspend fun runTask(
        task: TestTask,
        plan: BleTestPlan,
        eventSink: suspend (ProductionEvent) -> Unit
    ) {
        val semaphore = mutex.withLock { activeSemaphore }
        var acquired = false
        var enteredRunning = false
        var startedAt = 0L

        try {
            semaphore.acquire()
            acquired = true
            val canRun = mutex.withLock {
                if (task.sessionId != activeSessionId || activeSessionJob?.isActive != true) {
                    false
                } else {
                    true
                }
            }
            if (!canRun) {
                Log.d(TAG, "queue:cancel-before-run mac=${task.parsedMac.toString(16).uppercase()}")
                return
            }

            enteredRunning = true
            startedAt = System.currentTimeMillis()
            Log.d(TAG, "queue:run-start mac=${task.parsedMac.toString(16).uppercase()} addr=${task.deviceAddress}")
            val result = bleTestExecutor.execute(task, plan) { status ->
                Log.d(TAG, "queue:status mac=${task.parsedMac.toString(16).uppercase()} status=$status")
                eventSink(ProductionEvent.ExecutionStatusChanged(task.parsedMac, status))
            }
            finishTask(result, eventSink)
        } catch (cancelled: CancellationException) {
            Log.w(TAG, "queue:cancelled mac=${task.parsedMac.toString(16).uppercase()} reason=${cancelled.message}")
            if (enteredRunning) {
                eventSink(ProductionEvent.ExecutionAborted(task.parsedMac))
            }
            throw cancelled
        } catch (throwable: Throwable) {
            Log.e(TAG, "queue:run-error mac=${task.parsedMac.toString(16).uppercase()} reason=${throwable.message}", throwable)
            if (enteredRunning) {
                finishTask(
                    TestExecutionResult(
                        sessionId = task.sessionId,
                        batchId = task.batchId,
                        parsedMac = task.parsedMac,
                        deviceAddress = task.deviceAddress,
                        advName = task.advName,
                        rssi = task.rssi,
                        finalStatus = DeviceUiStatus.FAIL,
                        success = false,
                        reason = throwable.message ?: throwable.javaClass.simpleName,
                        failureReason = BleFailureReason.UNKNOWN,
                        startedAt = startedAt,
                        endedAt = System.currentTimeMillis()
                    ),
                    eventSink
                )
            }
        } finally {
            if (acquired) {
                semaphore.release()
            }
            Log.d(TAG, "queue:run-end mac=${task.parsedMac.toString(16).uppercase()}")
        }
    }

    private suspend fun finishTask(
        result: TestExecutionResult,
        eventSink: suspend (ProductionEvent) -> Unit
    ) {
        Log.d(
            TAG,
            "queue:finish mac=${result.parsedMac.toString(16).uppercase()} final=${result.finalStatus} " +
                "success=${result.success} reason=${result.reason}"
        )
        eventSink(ProductionEvent.ExecutionFinished(result))
    }

    private companion object {
        const val TAG = "TestQueueDispatcher"
    }
}
