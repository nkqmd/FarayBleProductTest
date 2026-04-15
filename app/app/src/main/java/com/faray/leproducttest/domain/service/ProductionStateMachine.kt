package com.faray.leproducttest.domain.service

import androidx.lifecycle.LiveData
import com.faray.leproducttest.model.BatchProfile
import com.faray.leproducttest.model.DeviceUiStatus
import com.faray.leproducttest.model.QueueSnapshot
import com.faray.leproducttest.model.ScanPayload
import com.faray.leproducttest.model.TestExecutionResult

interface ProductionStateMachine {
    suspend fun startSession(sessionId: String, profile: BatchProfile, staleAfterMs: Long)
    suspend fun dispatch(event: ProductionEvent)
    suspend fun stopSession()
    fun observeQueueState(): LiveData<QueueSnapshot>
}

sealed interface ProductionEvent {
    data class ScanSeen(val payload: ScanPayload) : ProductionEvent
    data class CleanupTick(val now: Long) : ProductionEvent
    data class ExecutionStatusChanged(val parsedMac: Long, val status: DeviceUiStatus) : ProductionEvent
    data class ExecutionFinished(val result: TestExecutionResult) : ProductionEvent
    data class ExecutionAborted(val parsedMac: Long) : ProductionEvent
}
