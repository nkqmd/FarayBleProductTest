package com.faray.leproducttest.ble.scan

import com.faray.leproducttest.model.BleTestPlan
import com.faray.leproducttest.model.ScanPayload

interface ScanScheduler {
    suspend fun start(
        sessionId: String,
        plan: BleTestPlan,
        onPayload: suspend (ScanPayload) -> Unit,
        onCycle: suspend (Long) -> Unit,
        onError: suspend (Int) -> Unit
    )

    suspend fun stop()
    fun isRunning(): Boolean
}
