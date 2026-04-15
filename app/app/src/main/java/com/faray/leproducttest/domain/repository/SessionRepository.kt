package com.faray.leproducttest.domain.repository

import com.faray.leproducttest.model.ProductionSession

interface SessionRepository {
    suspend fun createSession(session: ProductionSession): ProductionSession
    suspend fun markSessionStopped(sessionId: String, endedAt: Long)
    suspend fun markSessionUploaded(sessionId: String)
    suspend fun getSession(sessionId: String): ProductionSession?
    suspend fun getRunningSession(): ProductionSession?
    suspend fun getLatestFinishedSession(batchId: String): ProductionSession?
    suspend fun getLatestStoppedSession(batchId: String): ProductionSession?
    suspend fun getFinishedSessions(batchId: String): List<ProductionSession>
}
