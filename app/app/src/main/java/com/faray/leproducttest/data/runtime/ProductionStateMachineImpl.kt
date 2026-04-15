package com.faray.leproducttest.data.runtime

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.faray.leproducttest.ble.parser.AdvertisementParser
import com.faray.leproducttest.domain.repository.BatchRepository
import com.faray.leproducttest.domain.repository.TestRecordRepository
import com.faray.leproducttest.domain.service.ProductionEvent
import com.faray.leproducttest.domain.service.ProductionStateMachine
import com.faray.leproducttest.domain.service.RuntimeDeviceStore
import com.faray.leproducttest.domain.service.TestQueueDispatcher
import com.faray.leproducttest.model.BatchProfile
import com.faray.leproducttest.model.DeviceUiStatus
import com.faray.leproducttest.model.QueueSnapshot
import com.faray.leproducttest.model.RuntimeDeviceItem
import com.faray.leproducttest.model.ScanPayload
import com.faray.leproducttest.model.TestExecutionResult
import com.faray.leproducttest.model.TestTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ProductionStateMachineImpl(
    private val runtimeDeviceStore: RuntimeDeviceStore,
    private val batchRepository: BatchRepository,
    private val advertisementParser: AdvertisementParser,
    private val testRecordRepository: TestRecordRepository,
    private val testQueueDispatcher: TestQueueDispatcher
) : ProductionStateMachine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val queueState = MutableLiveData(QueueSnapshot(0, 0, 1))
    private var activeSession: ActiveSession? = null

    override suspend fun startSession(sessionId: String, profile: BatchProfile, staleAfterMs: Long) {
        stopSession()
        val channel = Channel<EventEnvelope>(capacity = Channel.UNLIMITED)
        val job = scope.launch {
            for (envelope in channel) {
                try {
                    processEvent(
                        session = envelope.session,
                        event = envelope.event
                    )
                    envelope.ack.complete(Unit)
                } catch (cancelled: CancellationException) {
                    envelope.ack.cancel(cancelled)
                    throw cancelled
                } catch (throwable: Throwable) {
                    envelope.ack.completeExceptionally(throwable)
                }
            }
        }
        mutex.withLock {
            activeSession = ActiveSession(
                sessionId = sessionId,
                profile = profile,
                staleAfterMs = staleAfterMs,
                channel = channel,
                job = job,
                queuedMacs = mutableSetOf(),
                runningMacs = mutableSetOf()
            )
        }
        queueState.postValue(QueueSnapshot(0, 0, profile.bleConfig.maxConcurrent.coerceAtLeast(1)))
    }

    override suspend fun dispatch(event: ProductionEvent) {
        val session = mutex.withLock { activeSession } ?: return
        val ack = CompletableDeferred<Unit>()
        try {
            session.channel.send(
                EventEnvelope(
                    session = session,
                    event = event,
                    ack = ack
                )
            )
        } catch (_: ClosedSendChannelException) {
            return
        }
        ack.await()
    }

    override suspend fun stopSession() {
        val session = mutex.withLock {
            val current = activeSession
            activeSession = null
            current
        } ?: return
        session.channel.close()
        session.job.join()
        val affectedMacs = (session.queuedMacs + session.runningMacs).toSet()
        affectedMacs.forEach { parsedMac ->
            runtimeDeviceStore.remove(parsedMac)
        }
        queueState.postValue(QueueSnapshot(0, 0, 1))
    }

    override fun observeQueueState(): LiveData<QueueSnapshot> = queueState

    private class ActiveSession(
        val sessionId: String,
        val profile: BatchProfile,
        val staleAfterMs: Long,
        val channel: Channel<EventEnvelope>,
        val job: Job,
        val queuedMacs: MutableSet<Long>,
        val runningMacs: MutableSet<Long>,
        var nextSequenceNo: Long = 1L
    )

    private data class EventEnvelope(
        val session: ActiveSession,
        val event: ProductionEvent,
        val ack: CompletableDeferred<Unit>
    )

    private suspend fun processEvent(
        session: ActiveSession,
        event: ProductionEvent
    ) {
        when (event) {
            is ProductionEvent.ScanSeen -> handleScanSeen(
                session = session,
                payload = event.payload
            )

            is ProductionEvent.CleanupTick -> handleCleanupTick(
                session = session,
                now = event.now
            )

            is ProductionEvent.ExecutionStatusChanged -> handleExecutionStatusChanged(
                session = session,
                parsedMac = event.parsedMac,
                status = event.status
            )

            is ProductionEvent.ExecutionFinished -> handleExecutionFinished(session, event.result)

            is ProductionEvent.ExecutionAborted -> {
                removeFromQueueTracking(session, event.parsedMac)
                runtimeDeviceStore.remove(event.parsedMac)
            }
        }
    }

    private suspend fun handleScanSeen(
        session: ActiveSession,
        payload: ScanPayload
    ) {
        if (payload.advName.isBlank()) {
            return
        }
        val plan = session.profile.bleConfig
        if (payload.rssi < plan.rssiMin) {
            return
        }
        if (plan.rssiMax != null && payload.rssi > plan.rssiMax) {
            return
        }

        val parsedMac = advertisementParser.parseMacFromName(
            advName = payload.advName,
            expectedPrefix = session.profile.bleNamePrefix
        ) ?: return
        Log.d(
            TAG,
            "scan:parsed session=${session.sessionId} mac=${parsedMac.toString(16).uppercase()} " +
                "addr=${payload.deviceAddress} adv=${payload.advName} rssi=${payload.rssi}"
        )

        val existing = runtimeDeviceStore.find(parsedMac)
        val stickyStatus = resolveStickyStatus(existing, payload)
        if (stickyStatus != null) {
            Log.d(
                TAG,
                "scan:keep-sticky mac=${parsedMac.toString(16).uppercase()} " +
                    "status=${existing?.uiStatus} nextStatus=$stickyStatus"
            )
            saveVisibleDevice(
                session = session,
                parsedMac = parsedMac,
                deviceAddress = payload.deviceAddress,
                advName = payload.advName,
                rssi = payload.rssi,
                seenAt = payload.seenAt,
                status = stickyStatus,
                retainUntilAt = existing?.retainUntilAt
            )
            return
        }

        val whitelisted = batchRepository.isMacWhitelisted(session.profile.batchId, parsedMac)
        if (!whitelisted) {
            transitionToInvalid(session, payload, parsedMac)
            return
        }

        if (testRecordRepository.hasFinalRecord(session.profile.batchId, parsedMac)) {
            transitionToAlreadyTested(session, payload, parsedMac, session.profile.batchId)
            return
        }

        transitionToQueued(session, payload, parsedMac)
    }

    private fun resolveStickyStatus(
        existing: RuntimeDeviceItem?,
        payload: ScanPayload
    ): DeviceUiStatus? {
        if (existing == null || existing.uiStatus !in stickyStatuses) {
            return null
        }
        return if (
            existing.uiStatus == DeviceUiStatus.PASS &&
            payload.seenAt - (existing.passAt ?: existing.lastSeenAt) >= PASS_VISIBLE_TO_ALREADY_TESTED_MS
        ) {
            DeviceUiStatus.ALREADY_TESTED
        } else {
            existing.uiStatus
        }
    }

    private suspend fun transitionToInvalid(
        session: ActiveSession,
        payload: ScanPayload,
        parsedMac: Long
    ) {
        Log.d(TAG, "scan:invalid-device mac=${parsedMac.toString(16).uppercase()} batch=${session.profile.batchId}")
        testRecordRepository.saveExecutionResult(
            TestExecutionResult(
                sessionId = session.sessionId,
                batchId = session.profile.batchId,
                parsedMac = parsedMac,
                deviceAddress = payload.deviceAddress,
                advName = payload.advName,
                rssi = payload.rssi,
                finalStatus = DeviceUiStatus.INVALID_DEVICE,
                success = false,
                reason = "Not in whitelist",
                failureReason = null,
                startedAt = payload.seenAt,
                endedAt = payload.seenAt
            )
        )
        saveVisibleDevice(
            session = session,
            parsedMac = parsedMac,
            deviceAddress = payload.deviceAddress,
            advName = payload.advName,
            rssi = payload.rssi,
            seenAt = payload.seenAt,
            status = DeviceUiStatus.INVALID_DEVICE
        )
    }

    private suspend fun transitionToAlreadyTested(
        session: ActiveSession,
        payload: ScanPayload,
        parsedMac: Long,
        batchId: String
    ) {
        Log.d(TAG, "scan:already-tested mac=${parsedMac.toString(16).uppercase()} batch=$batchId")
        saveVisibleDevice(
            session = session,
            parsedMac = parsedMac,
            deviceAddress = payload.deviceAddress,
            advName = payload.advName,
            rssi = payload.rssi,
            seenAt = payload.seenAt,
            status = DeviceUiStatus.ALREADY_TESTED
        )
    }

    private suspend fun transitionToQueued(
        session: ActiveSession,
        payload: ScanPayload,
        parsedMac: Long
    ) {
        Log.d(TAG, "scan:queue mac=${parsedMac.toString(16).uppercase()} batch=${session.profile.batchId}")
        saveVisibleDevice(
            session = session,
            parsedMac = parsedMac,
            deviceAddress = payload.deviceAddress,
            advName = payload.advName,
            rssi = payload.rssi,
            seenAt = payload.seenAt,
            status = DeviceUiStatus.QUEUED
        )
        val accepted = testQueueDispatcher.offer(
            TestTask(
                sessionId = session.sessionId,
                batchId = session.profile.batchId,
                parsedMac = parsedMac,
                deviceAddress = payload.deviceAddress,
                advName = payload.advName,
                rssi = payload.rssi,
                createdAt = payload.seenAt
            )
        )
        if (accepted) {
            session.queuedMacs += parsedMac
            publishQueueSnapshot(session)
        } else {
            runtimeDeviceStore.remove(parsedMac)
        }
        Log.d(TAG, "scan:queue-result mac=${parsedMac.toString(16).uppercase()} accepted=$accepted")
    }

    private suspend fun handleCleanupTick(
        session: ActiveSession,
        now: Long
    ) {
        runtimeDeviceStore.snapshot()
            .asSequence()
            .filter { item -> now - item.lastSeenAt > session.staleAfterMs }
            .map { it.parsedMac }
            .toList()
            .forEach { parsedMac ->
                removeFromQueueTracking(session, parsedMac)
                runtimeDeviceStore.remove(parsedMac)
            }
    }

    private suspend fun handleExecutionStatusChanged(
        session: ActiveSession,
        parsedMac: Long,
        status: DeviceUiStatus
    ) {
        val existing = runtimeDeviceStore.find(parsedMac) ?: return
        saveVisibleDevice(
            session = session,
            parsedMac = parsedMac,
            deviceAddress = existing.deviceAddress,
            advName = existing.advName,
            rssi = existing.rssi,
            seenAt = existing.lastSeenAt,
            status = status
        )
        moveToRunning(session, parsedMac)
    }

    private suspend fun handleExecutionFinished(
        session: ActiveSession,
        result: TestExecutionResult
    ) {
        removeFromQueueTracking(session, result.parsedMac)
        val retainUntilAt = when (result.finalStatus) {
            DeviceUiStatus.FAIL -> Long.MAX_VALUE
            else -> result.endedAt + TERMINAL_RETAIN_MS
        }
        Log.d(
            TAG,
            "execution:finish mac=${result.parsedMac.toString(16).uppercase()} " +
                "final=${result.finalStatus} success=${result.success} reason=${result.reason}"
        )
        testRecordRepository.saveExecutionResult(result)
        saveVisibleDevice(
            session = session,
            parsedMac = result.parsedMac,
            deviceAddress = result.deviceAddress,
            advName = result.advName,
            rssi = result.rssi,
            seenAt = result.endedAt,
            status = result.finalStatus,
            retainUntilAt = retainUntilAt
        )
    }

    private suspend fun saveVisibleDevice(
        session: ActiveSession,
        parsedMac: Long,
        deviceAddress: String,
        advName: String,
        rssi: Int,
        seenAt: Long,
        status: DeviceUiStatus,
        retainUntilAt: Long? = null
    ): RuntimeDeviceItem {
        val existing = runtimeDeviceStore.find(parsedMac)
        val item = RuntimeDeviceItem(
            parsedMac = parsedMac,
            deviceAddress = deviceAddress,
            advName = advName,
            rssi = rssi,
            lastSeenAt = seenAt,
            sequenceNo = existing?.sequenceNo ?: session.nextSequenceNo++,
            uiStatus = status,
            retainUntilAt = retainUntilAt,
            passAt = when (status) {
                DeviceUiStatus.PASS -> existing?.passAt ?: seenAt
                DeviceUiStatus.ALREADY_TESTED -> existing?.passAt
                else -> null
            }
        )
        return runtimeDeviceStore.save(item)
    }

    private fun moveToRunning(
        session: ActiveSession,
        parsedMac: Long
    ) {
        val removed = session.queuedMacs.remove(parsedMac)
        val added = session.runningMacs.add(parsedMac)
        if (removed || added) {
            publishQueueSnapshot(session)
        }
    }

    private fun removeFromQueueTracking(
        session: ActiveSession,
        parsedMac: Long
    ) {
        val removedQueued = session.queuedMacs.remove(parsedMac)
        val removedRunning = session.runningMacs.remove(parsedMac)
        if (removedQueued || removedRunning) {
            publishQueueSnapshot(session)
        }
    }

    private fun publishQueueSnapshot(session: ActiveSession) {
        queueState.postValue(
            QueueSnapshot(
                queuedCount = session.queuedMacs.size,
                runningCount = session.runningMacs.size,
                maxConcurrent = session.profile.bleConfig.maxConcurrent.coerceAtLeast(1)
            )
        )
    }

    private companion object {
        const val TAG = "ProductionStateMachine"
        const val PASS_VISIBLE_TO_ALREADY_TESTED_MS = 60_000L
        const val TERMINAL_RETAIN_MS = 3_000L
        val stickyStatuses = setOf(
            DeviceUiStatus.INVALID_DEVICE,
            DeviceUiStatus.QUEUED,
            DeviceUiStatus.CONNECTING,
            DeviceUiStatus.SUBSCRIBING,
            DeviceUiStatus.SENDING,
            DeviceUiStatus.WAITING_NOTIFY,
            DeviceUiStatus.DISCONNECTING,
            DeviceUiStatus.PASS,
            DeviceUiStatus.FAIL,
            DeviceUiStatus.ALREADY_TESTED
        )
    }
}
