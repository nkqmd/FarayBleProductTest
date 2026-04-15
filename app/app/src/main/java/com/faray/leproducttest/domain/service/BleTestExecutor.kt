package com.faray.leproducttest.domain.service

import com.faray.leproducttest.model.BleTestPlan
import com.faray.leproducttest.model.DeviceUiStatus
import com.faray.leproducttest.model.TestExecutionResult
import com.faray.leproducttest.model.TestTask

interface BleTestExecutor {
    suspend fun execute(
        task: TestTask,
        plan: BleTestPlan,
        onStatus: suspend (DeviceUiStatus) -> Unit
    ): TestExecutionResult
}
