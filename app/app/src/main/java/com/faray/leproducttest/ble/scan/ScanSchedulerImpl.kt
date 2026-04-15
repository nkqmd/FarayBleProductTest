package com.faray.leproducttest.ble.scan

import com.faray.leproducttest.model.BleTestPlan
import com.faray.leproducttest.model.ScanPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ScanSchedulerImpl(
    private val scannerEngine: BleScannerEngine
) : ScanScheduler {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var activeJob: Job? = null

    override suspend fun start(
        sessionId: String,
        plan: BleTestPlan,
        onPayload: suspend (ScanPayload) -> Unit,
        onCycle: suspend (Long) -> Unit,
        onError: suspend (Int) -> Unit
    ) {
        mutex.withLock {
            if (activeJob?.isActive == true) {
                return
            }
            activeJob = scope.launch {
                while (isActive) {
                    scannerEngine.start(
                        plan = plan,
                        onPayload = { payload ->
                            launch { onPayload(payload) }
                        },
                        onError = { errorCode ->
                            launch { onError(errorCode) }
                        }
                    )
                    delay(plan.scanActiveMs.coerceAtLeast(50L))
                    scannerEngine.stop()
                    onCycle(System.currentTimeMillis())
                    delay(plan.scanIdleMs.coerceAtLeast(0L))
                }
            }
        }
    }

    override suspend fun stop() {
        val job = mutex.withLock {
            val current = activeJob
            activeJob = null
            current
        }
        job?.cancelAndJoin()
        scannerEngine.stop()
    }

    override fun isRunning(): Boolean = activeJob?.isActive == true
}
