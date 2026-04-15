package com.faray.leproducttest.data.repository

import com.faray.leproducttest.data.local.dao.ProductionSessionDao
import com.faray.leproducttest.data.local.entity.ProductionSessionEntity
import com.faray.leproducttest.domain.repository.SessionRepository
import com.faray.leproducttest.model.ProductionSession
import com.faray.leproducttest.model.SessionStatus

class SessionRepositoryImpl(
    private val productionSessionDao: ProductionSessionDao
) : SessionRepository {

    override suspend fun createSession(session: ProductionSession): ProductionSession {
        productionSessionDao.upsert(session.toEntity())
        return session
    }

    override suspend fun markSessionStopped(sessionId: String, endedAt: Long) {
        val running = productionSessionDao.getById(sessionId) ?: return
        productionSessionDao.upsert(
            running.copy(
                endedAt = endedAt,
                status = SessionStatus.STOPPED.name
            )
        )
    }

    override suspend fun markSessionUploaded(sessionId: String) {
        val existing = productionSessionDao.getById(sessionId) ?: return
        productionSessionDao.upsert(existing.copy(status = SessionStatus.UPLOADED.name))
    }

    override suspend fun getSession(sessionId: String): ProductionSession? {
        return productionSessionDao.getById(sessionId)?.toDomain()
    }

    override suspend fun getRunningSession(): ProductionSession? {
        return productionSessionDao.getByStatus(SessionStatus.RUNNING.name)?.toDomain()
    }

    override suspend fun getLatestFinishedSession(batchId: String): ProductionSession? {
        return productionSessionDao.getLatestByBatchAndStatuses(
            batchId = batchId,
            statuses = listOf(SessionStatus.STOPPED.name, SessionStatus.UPLOADED.name)
        )?.toDomain()
    }

    override suspend fun getLatestStoppedSession(batchId: String): ProductionSession? {
        return productionSessionDao.getLatestByBatchAndStatus(
            batchId = batchId,
            status = SessionStatus.STOPPED.name
        )?.toDomain()
    }

    override suspend fun getFinishedSessions(batchId: String): List<ProductionSession> {
        return productionSessionDao.getFinishedByBatch(batchId).map { it.toDomain() }
    }

    private fun ProductionSession.toEntity(): ProductionSessionEntity {
        return ProductionSessionEntity(
            sessionId = sessionId,
            batchId = batchId,
            factoryId = factoryId,
            startedAt = startedAt,
            endedAt = endedAt,
            status = status.name
        )
    }

    private fun ProductionSessionEntity.toDomain(): ProductionSession {
        return ProductionSession(
            sessionId = sessionId,
            batchId = batchId,
            factoryId = factoryId,
            startedAt = startedAt,
            endedAt = endedAt,
            status = SessionStatus.valueOf(status)
        )
    }
}
