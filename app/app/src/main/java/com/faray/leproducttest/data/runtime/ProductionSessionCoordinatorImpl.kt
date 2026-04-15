package com.faray.leproducttest.data.runtime

import com.faray.leproducttest.ble.scan.ScanScheduler
import com.faray.leproducttest.domain.repository.SessionRepository
import com.faray.leproducttest.domain.service.ProductionEvent
import com.faray.leproducttest.domain.service.ProductionSessionCoordinator
import com.faray.leproducttest.domain.service.ProductionStateMachine
import com.faray.leproducttest.domain.service.RuntimeDeviceStore
import com.faray.leproducttest.domain.service.TestQueueDispatcher
import com.faray.leproducttest.model.BatchProfile
import com.faray.leproducttest.model.ProductionSession

class ProductionSessionCoordinatorImpl(
    private val scanScheduler: ScanScheduler,
    private val testQueueDispatcher: TestQueueDispatcher,
    private val productionStateMachine: ProductionStateMachine,
    private val runtimeDeviceStore: RuntimeDeviceStore,
    private val sessionRepository: SessionRepository
) : ProductionSessionCoordinator {

    override suspend fun startSession(
        session: ProductionSession,
        profile: BatchProfile,
        staleTimeoutMs: Long,
        onScanError: suspend (Int) -> Unit
    ) {
        scanScheduler.stop()
        testQueueDispatcher.stop()
        productionStateMachine.stopSession()
        runtimeDeviceStore.clear()
        sessionRepository.createSession(session)
        productionStateMachine.startSession(
            sessionId = session.sessionId,
            profile = profile,
            staleAfterMs = staleTimeoutMs
        )
        testQueueDispatcher.startSession(
            sessionId = session.sessionId,
            plan = profile.bleConfig,
            onEvent = productionStateMachine::dispatch
        )
        scanScheduler.start(
            sessionId = session.sessionId,
            plan = profile.bleConfig,
            onPayload = { payload ->
                productionStateMachine.dispatch(ProductionEvent.ScanSeen(payload))
            },
            onCycle = { now ->
                productionStateMachine.dispatch(ProductionEvent.CleanupTick(now))
            },
            onError = onScanError
        )
    }

    override suspend fun stopSession(
        sessionId: String?,
        endedAt: Long
    ) {
        scanScheduler.stop()
        testQueueDispatcher.stop()
        productionStateMachine.stopSession()
        if (!sessionId.isNullOrBlank()) {
            sessionRepository.markSessionStopped(
                sessionId = sessionId,
                endedAt = endedAt
            )
        }
    }
}
