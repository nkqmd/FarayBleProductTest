package com.faray.leproducttest.domain.service

import com.faray.leproducttest.model.BatchProfile
import com.faray.leproducttest.model.ProductionSession

interface ProductionSessionCoordinator {
    suspend fun startSession(
        session: ProductionSession,
        profile: BatchProfile,
        staleTimeoutMs: Long,
        onScanError: suspend (Int) -> Unit
    )

    suspend fun stopSession(
        sessionId: String?,
        endedAt: Long
    )
}
