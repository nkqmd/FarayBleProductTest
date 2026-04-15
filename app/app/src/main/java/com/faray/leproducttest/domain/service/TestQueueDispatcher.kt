package com.faray.leproducttest.domain.service

import com.faray.leproducttest.model.BleTestPlan
import com.faray.leproducttest.model.TestTask

interface TestQueueDispatcher {
    suspend fun startSession(
        sessionId: String,
        plan: BleTestPlan,
        onEvent: suspend (ProductionEvent) -> Unit
    )

    suspend fun offer(task: TestTask): Boolean
    suspend fun stop()
}
