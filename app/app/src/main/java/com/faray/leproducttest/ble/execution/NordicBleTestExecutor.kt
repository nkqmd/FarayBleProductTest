package com.faray.leproducttest.ble.execution

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import com.faray.leproducttest.common.HexCodec
import com.faray.leproducttest.domain.service.BleTestExecutor
import com.faray.leproducttest.model.BleFailureReason
import com.faray.leproducttest.model.BleTestPlan
import com.faray.leproducttest.model.DeviceUiStatus
import com.faray.leproducttest.model.TestExecutionResult
import com.faray.leproducttest.model.TestTask
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.callback.DataReceivedCallback
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

class NordicBleTestExecutor(
    context: Context
) : BleTestExecutor {

    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)

    override suspend fun execute(
        task: TestTask,
        plan: BleTestPlan,
        onStatus: suspend (DeviceUiStatus) -> Unit
    ): TestExecutionResult = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val maxAttempts = BleRetryPolicy.FIXED_MAX_ATTEMPTS
        Log.d(
            TAG,
            "execute:start session=${task.sessionId} mac=${task.parsedMac.toString(16).uppercase()} " +
                "addr=${task.deviceAddress} adv=${task.advName} rssi=${task.rssi} " +
                "service=${plan.serviceUuid} notify=${plan.notifyCharacteristicUuid} write=${plan.writeCharacteristicUuid} " +
                "configuredRetryMax=${plan.retryMax} effectiveMaxAttempts=$maxAttempts retryIntervalMs=${plan.retryIntervalMs}"
        )
        val payloadBytes = requireNotNull(HexCodec.parse(plan.writePayloadHex)) {
            "Invalid write payload hex"
        }
        val expectedNotifyHex = plan.expectedNotifyValueHex?.let {
            requireNotNull(HexCodec.normalize(it)) { "Invalid expected notify hex" }
        }
        val device = requireNotNull(bluetoothManager?.adapter?.getRemoteDevice(task.deviceAddress)) {
            "Bluetooth adapter is not available"
        }

        var attempt = 1
        while (attempt <= maxAttempts) {
            var manager: DutBleManager? = null
            var phase = ExecutionPhase.CONNECTING
            try {
                manager = DutBleManager(
                    context = appContext,
                    serviceUuid = UUID.fromString(plan.serviceUuid),
                    notifyCharacteristicUuid = UUID.fromString(plan.notifyCharacteristicUuid),
                    writeCharacteristicUuid = UUID.fromString(plan.writeCharacteristicUuid)
                )
                Log.d(TAG, "execute:attempt-start addr=${task.deviceAddress} attempt=$attempt/$maxAttempts")
                val finalStatus = withTimeout(plan.overallTimeoutMs.coerceAtLeast(plan.connectTimeoutMs)) {
                    phase = ExecutionPhase.CONNECTING
                    onStatus(DeviceUiStatus.CONNECTING)
                    Log.d(TAG, "execute:connecting addr=${task.deviceAddress} attempt=$attempt/$maxAttempts")
                    runCatching {
                        manager.connect(device)
                            .timeout(plan.connectTimeoutMs)
                            .await()
                    }.getOrElse { connectError ->
                        throw ConnectFailureException(connectError)
                    }
                    manager.requiredServicesFailureReason()?.let { supportFailure ->
                        throw SupportFailureException(supportFailure)
                    }
                    Log.d(TAG, "execute:connected addr=${task.deviceAddress} attempt=$attempt/$maxAttempts")

                    phase = ExecutionPhase.SUBSCRIBING
                    onStatus(DeviceUiStatus.SUBSCRIBING)
                    Log.d(TAG, "execute:subscribing addr=${task.deviceAddress} attempt=$attempt/$maxAttempts")
                    runCatching {
                        manager.setNotificationCallbackForTest()
                        manager.enableNotificationsForTest().await()
                    }.getOrElse { subscribeError ->
                        throw SubscribeFailureException(subscribeError)
                    }
                    Log.d(TAG, "execute:subscribed addr=${task.deviceAddress} attempt=$attempt/$maxAttempts")

                    phase = ExecutionPhase.SENDING
                    onStatus(DeviceUiStatus.SENDING)
                    val notifyBytes = AtomicReference<ByteArray?>()
                    val writeRequest = runCatching {
                        manager.writePayloadForTest(payloadBytes)
                    }.getOrElse { writeError ->
                        throw WriteFailureException(writeError)
                    }
                    val notificationRequest = manager.waitForNotificationForTest()
                        .timeout(plan.notifyTimeoutMs)
                        .trigger(writeRequest)
                        .with(DataReceivedCallback { _, data ->
                            notifyBytes.set(data.value)
                            Log.d(
                                TAG,
                                "execute:notify-received addr=${task.deviceAddress} attempt=$attempt/$maxAttempts value=${HexCodec.toHex(data.value) ?: "null"}"
                            )
                        })
                    Log.d(TAG, "execute:writing addr=${task.deviceAddress} attempt=$attempt/$maxAttempts payload=${plan.writePayloadHex}")

                    phase = ExecutionPhase.WAITING_NOTIFY
                    onStatus(DeviceUiStatus.WAITING_NOTIFY)
                    runCatching {
                        notificationRequest.await()
                    }.getOrElse { waitError ->
                        throw NotifyTimeoutException(waitError)
                    }

                    val actualNotifyHex = HexCodec.toHex(notifyBytes.get())
                    Log.d(
                        TAG,
                        "execute:notify-complete addr=${task.deviceAddress} attempt=$attempt/$maxAttempts actual=$actualNotifyHex expected=$expectedNotifyHex"
                    )
                    if (expectedNotifyHex != null && actualNotifyHex != expectedNotifyHex) {
                        throw NotifyMismatchException(
                            "Unexpected notify payload: expected=$expectedNotifyHex actual=${actualNotifyHex ?: "null"}"
                        )
                    }
                    DeviceUiStatus.PASS
                }
                Log.d(TAG, "execute:pass addr=${task.deviceAddress} attempt=$attempt/$maxAttempts")

                return@withContext TestExecutionResult(
                    sessionId = task.sessionId,
                    batchId = task.batchId,
                    parsedMac = task.parsedMac,
                    deviceAddress = task.deviceAddress,
                    advName = task.advName,
                    rssi = task.rssi,
                    finalStatus = finalStatus,
                    success = finalStatus == DeviceUiStatus.PASS,
                    reason = null,
                    failureReason = null,
                    startedAt = startedAt,
                    endedAt = System.currentTimeMillis()
                )
            } catch (throwable: Throwable) {
                val failureReason = classifyFailure(throwable, phase, manager)
                val shouldRetry = BleRetryPolicy.shouldRetry(failureReason, attempt, maxAttempts)
                logFailure(task, throwable, failureReason, attempt, maxAttempts, shouldRetry)
                if (!shouldRetry) {
                    return@withContext buildFailureResult(task, startedAt, throwable, failureReason)
                }
                if (plan.retryIntervalMs > 0L) {
                    Log.d(
                        TAG,
                        "execute:retry-wait addr=${task.deviceAddress} delayMs=${plan.retryIntervalMs} nextAttempt=${attempt + 1}/$maxAttempts"
                    )
                    delay(plan.retryIntervalMs)
                }
            } finally {
                manager?.let { disconnectQuietly(it, task, onStatus) }
            }
            attempt += 1
        }

        buildFailureResult(
            task = task,
            startedAt = startedAt,
            throwable = IllegalStateException("Retry loop exited unexpectedly"),
            failureReason = BleFailureReason.UNKNOWN
        )
    }

    private suspend fun disconnectQuietly(
        manager: DutBleManager,
        task: TestTask,
        onStatus: suspend (DeviceUiStatus) -> Unit
    ) {
        runCatching {
            if (manager.isConnected) {
                onStatus(DeviceUiStatus.DISCONNECTING)
                Log.d(TAG, "execute:disconnecting addr=${task.deviceAddress}")
                manager.disconnect().await()
                Log.d(TAG, "execute:disconnected addr=${task.deviceAddress}")
            }
        }.onFailure { disconnectError ->
            Log.w(TAG, "execute:disconnect-error addr=${task.deviceAddress} reason=${disconnectError.message}", disconnectError)
        }
        manager.close()
    }

    private fun logFailure(
        task: TestTask,
        throwable: Throwable,
        failureReason: BleFailureReason,
        attempt: Int,
        maxAttempts: Int,
        shouldRetry: Boolean
    ) {
        val failureMessage = "execute:fail session=${task.sessionId} mac=${task.parsedMac.toString(16).uppercase()} " +
            "addr=${task.deviceAddress} attempt=$attempt/$maxAttempts willRetry=$shouldRetry " +
            "failureReason=$failureReason reason=${throwable.javaClass.simpleName}: ${throwable.message}"
        if (throwable is NotifyMismatchException) {
            Log.w(TAG, failureMessage)
        } else {
            Log.e(TAG, failureMessage, throwable)
        }
    }

    private fun buildFailureResult(
        task: TestTask,
        startedAt: Long,
        throwable: Throwable,
        failureReason: BleFailureReason
    ): TestExecutionResult {
        return TestExecutionResult(
            sessionId = task.sessionId,
            batchId = task.batchId,
            parsedMac = task.parsedMac,
            deviceAddress = task.deviceAddress,
            advName = task.advName,
            rssi = task.rssi,
            finalStatus = DeviceUiStatus.FAIL,
            success = false,
            reason = throwable.message ?: throwable.javaClass.simpleName,
            failureReason = failureReason,
            startedAt = startedAt,
            endedAt = System.currentTimeMillis()
        )
    }

    private fun classifyFailure(
        throwable: Throwable,
        phase: ExecutionPhase,
        manager: DutBleManager?
    ): BleFailureReason {
        return when (throwable) {
            is SupportFailureException -> throwable.failureReason
            else -> manager?.requiredServicesFailureReason()
        } ?: when (throwable) {
            is ConnectFailureException -> BleFailureReason.CONNECT_FAILED
            is SubscribeFailureException -> BleFailureReason.SUBSCRIBE_FAILED
            is WriteFailureException -> BleFailureReason.WRITE_FAILED
            is NotifyTimeoutException -> BleFailureReason.NOTIFY_TIMEOUT
            is NotifyMismatchException -> BleFailureReason.NOTIFY_MISMATCH
            is SessionStoppedException -> BleFailureReason.STOPPED_BY_SESSION
            else -> when (phase) {
                ExecutionPhase.CONNECTING -> BleFailureReason.CONNECT_FAILED
                ExecutionPhase.SUBSCRIBING -> BleFailureReason.SUBSCRIBE_FAILED
                ExecutionPhase.SENDING -> BleFailureReason.WRITE_FAILED
                ExecutionPhase.WAITING_NOTIFY -> BleFailureReason.NOTIFY_TIMEOUT
            }
        }
    }

    private class DutBleManager(
        context: Context,
        private val serviceUuid: UUID,
        private val notifyCharacteristicUuid: UUID,
        private val writeCharacteristicUuid: UUID
    ) : BleManager(context) {

        private var notifyCharacteristic: BluetoothGattCharacteristic? = null
        private var writeCharacteristic: BluetoothGattCharacteristic? = null
        private var requiredServicesFailureReason: BleFailureReason? = null

        override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
            val service = gatt.getService(serviceUuid)
            if (service == null) {
                Log.w(TAG, "gatt:service-missing service=$serviceUuid")
                requiredServicesFailureReason = BleFailureReason.SERVICE_NOT_FOUND
                return false
            }
            notifyCharacteristic = service.getCharacteristic(notifyCharacteristicUuid)
            writeCharacteristic = service.getCharacteristic(writeCharacteristicUuid)
            requiredServicesFailureReason = if (notifyCharacteristic == null || writeCharacteristic == null) {
                BleFailureReason.CHARACTERISTIC_NOT_FOUND
            } else {
                null
            }
            Log.d(
                TAG,
                "gatt:service-supported service=$serviceUuid notifyFound=${notifyCharacteristic != null} writeFound=${writeCharacteristic != null}"
            )
            return requiredServicesFailureReason == null
        }

        override fun onServicesInvalidated() {
            Log.d(TAG, "gatt:services-invalidated")
            notifyCharacteristic = null
            writeCharacteristic = null
            requiredServicesFailureReason = null
        }

        fun setNotificationCallbackForTest() {
            setNotificationCallback(requireNotifyCharacteristic())
        }

        fun enableNotificationsForTest() =
            enableNotifications(requireNotifyCharacteristic())

        fun writePayloadForTest(payloadBytes: ByteArray) =
            writeCharacteristic(requireWriteCharacteristic(), payloadBytes)

        fun waitForNotificationForTest() =
            waitForNotification(requireNotifyCharacteristic())

        fun requiredServicesFailureReason(): BleFailureReason? = requiredServicesFailureReason

        private fun requireNotifyCharacteristic(): BluetoothGattCharacteristic {
            return requireNotNull(notifyCharacteristic) {
                "Notify characteristic is unavailable"
            }
        }

        private fun requireWriteCharacteristic(): BluetoothGattCharacteristic {
            return requireNotNull(writeCharacteristic) {
                "Write characteristic is unavailable"
            }
        }
    }

    private companion object {
        const val TAG = "BleTestExecutor"
    }

    private enum class ExecutionPhase {
        CONNECTING,
        SUBSCRIBING,
        SENDING,
        WAITING_NOTIFY
    }

    private class ConnectFailureException(cause: Throwable) : IllegalStateException(cause.message, cause)
    private class SubscribeFailureException(cause: Throwable) : IllegalStateException(cause.message, cause)
    private class WriteFailureException(cause: Throwable) : IllegalStateException(cause.message, cause)
    private class NotifyTimeoutException(cause: Throwable) : IllegalStateException(cause.message, cause)
    private class SupportFailureException(val failureReason: BleFailureReason) : IllegalStateException(failureReason.name)
    private class NotifyMismatchException(message: String) : IllegalStateException(message)
    private class SessionStoppedException(message: String) : IllegalStateException(message)
}
