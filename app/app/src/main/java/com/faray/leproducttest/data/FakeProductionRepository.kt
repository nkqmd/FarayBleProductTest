package com.faray.leproducttest.data

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.faray.leproducttest.model.BatchProfile
import com.faray.leproducttest.model.BleTestPlan
import com.faray.leproducttest.model.DeviceUiStatus
import com.faray.leproducttest.model.DeviceUiWarning
import com.faray.leproducttest.model.RuntimeDeviceItem
import com.faray.leproducttest.model.SessionStatus
import com.faray.leproducttest.ui.config.ConfigUiState
import com.faray.leproducttest.ui.login.AuthUiState
import com.faray.leproducttest.ui.production.ProductionUiState
import com.faray.leproducttest.ui.result.ResultUiState
import com.faray.leproducttest.ui.shared.ShellUiState
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object FakeProductionRepository {

    private val handler = Handler(Looper.getMainLooper())
    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val compactFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

    private val authStateData = MutableLiveData(AuthUiState())
    private val configStateData = MutableLiveData(ConfigUiState())
    private val productionStateData = MutableLiveData(ProductionUiState())
    private val resultStateData = MutableLiveData(
        ResultUiState(
            uploadStatus = "Pending upload",
            uploadMessage = "Finish one production session before uploading the result"
        )
    )
    private val shellStateData = MutableLiveData(ShellUiState())

    val authState: LiveData<AuthUiState> = authStateData
    val configState: LiveData<ConfigUiState> = configStateData
    val productionState: LiveData<ProductionUiState> = productionStateData
    val resultState: LiveData<ResultUiState> = resultStateData
    val shellState: LiveData<ShellUiState> = shellStateData

    private var lastUploadedSessionId: String? = null

    fun adoptConfigState(state: ConfigUiState) {
        configStateData.value = state
        shellStateData.value = (shellStateData.value ?: ShellUiState()).copy(
            batchId = state.batchId.ifBlank { null },
            factoryId = state.factoryId.ifBlank { null },
            canStartProduction = state.canStartProduction
        )
        if (state.currentBatch != null) {
            resultStateData.value = (resultStateData.value ?: ResultUiState()).copy(
                batchId = state.batchId,
                expectedCount = state.currentBatch.expectedCount,
                uploadEnabled = false,
                uploadStatus = "Pending upload",
                uploadMessage = "Stop production before uploading the session result",
                uploadId = null,
                duplicate = false
            )
        }
    }

    fun login(username: String, password: String) {
        authStateData.value = authStateData.value?.copy(
            authenticating = true,
            errorMessage = null
        )
        handler.postDelayed({
            val shouldFail = username.trim().equals("error", ignoreCase = true) || password.trim() != "123456"
            if (shouldFail) {
                authStateData.value = AuthUiState(
                    authenticated = false,
                    authenticating = false,
                    errorMessage = "Login failed. Check username or password."
                )
                shellStateData.value = (shellStateData.value ?: ShellUiState()).copy(authenticated = false)
            } else {
                authStateData.value = AuthUiState(
                    authenticated = true,
                    authenticating = false,
                    accessTokenExpiresAt = System.currentTimeMillis() + 60 * 60 * 1000,
                    refreshTokenExpiresAt = System.currentTimeMillis() + 24 * 60 * 60 * 1000,
                    errorMessage = null
                )
                shellStateData.value = (shellStateData.value ?: ShellUiState()).copy(authenticated = true)
            }
        }, 900L)
    }

    fun loadBatch(factoryId: String, batchId: String) {
        if (shellStateData.value?.navigationLocked == true) {
            return
        }
        val cleanFactoryId = factoryId.trim()
        val cleanBatchId = batchId.trim()
        adoptConfigState(
            ConfigUiState(
                loading = true,
                importProgress = 0,
                factoryId = cleanFactoryId,
                batchId = cleanBatchId,
                summaryStatus = "Loading batch summary...",
                macListStatus = "Waiting for MAC list download",
                canStartProduction = false
            )
        )

        listOf(20, 45, 70, 100).forEachIndexed { index, progress ->
            handler.postDelayed({
                configStateData.value = (configStateData.value ?: ConfigUiState()).copy(
                    importProgress = progress,
                    summaryStatus = if (progress >= 45) "Batch summary loaded" else "Loading batch summary...",
                    macListStatus = when {
                        progress < 45 -> "Waiting for MAC list download"
                        progress < 100 -> "Downloading MAC list..."
                        else -> "MAC list downloaded"
                    }
                )
            }, (index + 1) * 280L)
        }

        handler.postDelayed({
            val profile = fakeBatchProfile(cleanBatchId)
            adoptConfigState(
                ConfigUiState(
                    loading = false,
                    importProgress = 100,
                    currentBatch = profile,
                    factoryId = cleanFactoryId,
                    batchId = cleanBatchId,
                    summaryStatus = "Batch summary loaded",
                    macListStatus = "MAC list downloaded",
                    importedCount = profile.macListCount,
                    lastUpdatedAt = nowString(),
                    errorMessage = null,
                    canStartProduction = true
                )
            )
        }, 1500L)
    }

    fun startProduction() {
        val config = configStateData.value ?: ConfigUiState()
        if (!config.canStartProduction || config.currentBatch == null) {
            productionStateData.value = (productionStateData.value ?: ProductionUiState()).copy(
                errorMessage = "Load a batch on the config page first"
            )
            return
        }
        val sessionId = "SESSION_${LocalDateTime.now().format(compactFormatter)}"
        val devices = runningDevices()
        val metrics = calculateMetrics(devices)

        productionStateData.value = ProductionUiState(
            sessionStatus = SessionStatus.RUNNING,
            scanning = true,
            devices = devices,
            queueSize = devices.count { it.uiStatus == DeviceUiStatus.QUEUED },
            runningCount = devices.count { it.uiStatus in activeTestingStatuses },
            maxConcurrent = config.currentBatch.bleConfig.maxConcurrent,
            successCount = metrics.successCount,
            failCount = metrics.failCount,
            invalidCount = metrics.invalidCount,
            totalCount = metrics.totalCount,
            actualCount = metrics.actualCount,
            successRate = metrics.successRate,
            navigationLocked = true,
            errorMessage = null
        )
        resultStateData.value = ResultUiState(
            sessionId = sessionId,
            batchId = config.batchId,
            expectedCount = config.currentBatch.expectedCount,
            actualCount = metrics.actualCount,
            successCount = metrics.successCount,
            failCount = metrics.failCount,
            invalidCount = metrics.invalidCount,
            successRate = metrics.successRate,
            uploadEnabled = false,
            uploading = false,
            uploadStatus = "Pending upload",
            uploadMessage = "Stop production before uploading the session result",
            uploadId = null,
            duplicate = false
        )
        shellStateData.value = (shellStateData.value ?: ShellUiState()).copy(
            activeSessionId = sessionId,
            navigationLocked = true
        )
    }

    fun stopProduction() {
        val current = productionStateData.value ?: return
        if (current.sessionStatus != SessionStatus.RUNNING && current.sessionStatus != SessionStatus.STOPPING) {
            return
        }
        productionStateData.value = current.copy(
            sessionStatus = SessionStatus.STOPPING,
            scanning = false,
            navigationLocked = true,
            errorMessage = null
        )
        handler.postDelayed({
            val devices = stoppedDevices()
            val metrics = calculateMetrics(devices)
            val sessionId = shellStateData.value?.activeSessionId
            val batchId = shellStateData.value?.batchId
            val expectedCount = configStateData.value?.currentBatch?.expectedCount ?: 0
            productionStateData.value = ProductionUiState(
                sessionStatus = SessionStatus.STOPPED,
                scanning = false,
                devices = devices,
                queueSize = 0,
                runningCount = 0,
                maxConcurrent = configStateData.value?.currentBatch?.bleConfig?.maxConcurrent ?: 1,
                successCount = metrics.successCount,
                failCount = metrics.failCount,
                invalidCount = metrics.invalidCount,
                totalCount = metrics.totalCount,
                actualCount = metrics.actualCount,
                successRate = metrics.successRate,
                navigationLocked = false,
                errorMessage = null
            )
            resultStateData.value = ResultUiState(
                sessionId = sessionId,
                batchId = batchId,
                expectedCount = expectedCount,
                actualCount = metrics.actualCount,
                successCount = metrics.successCount,
                failCount = metrics.failCount,
                invalidCount = metrics.invalidCount,
                successRate = metrics.successRate,
                uploadEnabled = true,
                uploading = false,
                uploadStatus = "Pending upload",
                uploadMessage = "Upload is available for the latest stopped session",
                uploadId = null,
                duplicate = false
            )
            shellStateData.value = (shellStateData.value ?: ShellUiState()).copy(navigationLocked = false)
        }, 700L)
    }

    fun uploadResult() {
        val current = resultStateData.value ?: return
        if (!current.uploadEnabled || current.sessionId.isNullOrBlank()) {
            return
        }
        resultStateData.value = current.copy(
            uploading = true,
            uploadStatus = "Uploading",
            uploadMessage = "Building report payload"
        )
        handler.postDelayed({
            val duplicate = lastUploadedSessionId == current.sessionId
            if (!duplicate) {
                lastUploadedSessionId = current.sessionId
            }
            resultStateData.value = current.copy(
                uploading = false,
                uploadEnabled = true,
                uploadStatus = if (duplicate) "Duplicate upload" else "Upload success",
                uploadMessage = if (duplicate) {
                    "The server already has this report"
                } else {
                    "The session result has been uploaded"
                },
                uploadId = "UPL_${LocalDateTime.now().format(compactFormatter)}",
                duplicate = duplicate
            )
            val production = productionStateData.value
            if (production?.sessionStatus == SessionStatus.STOPPED) {
                productionStateData.value = production.copy(sessionStatus = SessionStatus.UPLOADED)
            }
        }, 900L)
    }

    private fun fakeBatchProfile(batchId: String): BatchProfile {
        return BatchProfile(
            batchId = batchId,
            expectedCount = 120,
            expireTime = "2026-04-30 18:00:00",
            expectedFirmware = "1.0.3",
            deviceType = "CL-Tester",
            bleNamePrefix = "CL-XXF-",
            bleNameRule = "prefix + 12HEX",
            bleConfig = BleTestPlan(
                rssiMin = -70,
                rssiMax = -35,
                scanIdleMs = 100,
                scanActiveMs = 50,
                maxConcurrent = 2,
                connectTimeoutMs = 5_000,
                notifyTimeoutMs = 2_000,
                overallTimeoutMs = 8_000,
                serviceUuid = "0000180F-0000-1000-8000-00805F9B34FB",
                notifyCharacteristicUuid = "00002A19-0000-1000-8000-00805F9B34FB",
                writeCharacteristicUuid = "00002A1A-0000-1000-8000-00805F9B34FB",
                writePayloadHex = "AA55FF00",
                expectedNotifyValueHex = "CC33"
            ),
            rawBleConfigJson = """{"max_concurrent":2,"rssi_min":-70}""",
            macListCount = 100_000,
            macListHash = "a53c99ef-demo",
            macListVersion = "2026.04.09",
            macListUrl = "https://demo.local/$batchId/${batchId}_mac_list.txt"
        )
    }

    private fun runningDevices(): List<RuntimeDeviceItem> {
        val now = System.currentTimeMillis()
        return listOf(
            device("010203040506", -48, 1, DeviceUiStatus.PASS, now),
            device("010203040507", -59, 2, DeviceUiStatus.FAIL, now),
            device("010203040508", -63, 3, DeviceUiStatus.INVALID_DEVICE, now),
            device("010203040509", -55, 4, DeviceUiStatus.QUEUED, now),
            device("01020304050A", -52, 5, DeviceUiStatus.WAITING_NOTIFY, now, DeviceUiWarning.DUPLICATE_MAC_IN_PROGRESS),
            device("01020304050B", -57, 6, DeviceUiStatus.ALREADY_TESTED, now),
            device("01020304050C", -54, 7, DeviceUiStatus.CONNECTING, now)
        )
    }

    private fun stoppedDevices(): List<RuntimeDeviceItem> {
        val now = System.currentTimeMillis()
        return listOf(
            device("010203040506", -48, 1, DeviceUiStatus.PASS, now),
            device("010203040507", -59, 2, DeviceUiStatus.FAIL, now),
            device("010203040508", -63, 3, DeviceUiStatus.INVALID_DEVICE, now),
            device("010203040509", -55, 4, DeviceUiStatus.PASS, now),
            device("01020304050A", -52, 5, DeviceUiStatus.PASS, now),
            device("01020304050B", -57, 6, DeviceUiStatus.ALREADY_TESTED, now),
            device("01020304050C", -54, 7, DeviceUiStatus.FAIL, now)
        )
    }

    private fun device(
        hexMac: String,
        rssi: Int,
        sequenceNo: Long,
        status: DeviceUiStatus,
        now: Long,
        warning: DeviceUiWarning? = null
    ): RuntimeDeviceItem {
        return RuntimeDeviceItem(
            parsedMac = hexMac.toLong(16),
            deviceAddress = "AA:BB:CC:DD:${hexMac.takeLast(4).chunked(2).joinToString(":")}",
            advName = "CL-XXF-$hexMac",
            rssi = rssi,
            lastSeenAt = now,
            sequenceNo = sequenceNo,
            uiStatus = status,
            warning = warning,
            retainUntilAt = now + 3_000
        )
    }

    private fun calculateMetrics(devices: List<RuntimeDeviceItem>): Metrics {
        val successCount = devices.count { it.uiStatus == DeviceUiStatus.PASS }
        val failCount = devices.count { it.uiStatus == DeviceUiStatus.FAIL }
        val invalidCount = devices.count { it.uiStatus == DeviceUiStatus.INVALID_DEVICE }
        val testingCount = devices.count { it.uiStatus in activeTestingStatuses }
        val actualCount = successCount + failCount + testingCount
        val totalCount = devices.size
        val successRate = if (actualCount == 0) 0.0 else successCount * 100.0 / actualCount
        return Metrics(
            totalCount = totalCount,
            actualCount = actualCount,
            successCount = successCount,
            failCount = failCount,
            invalidCount = invalidCount,
            successRate = successRate
        )
    }

    private fun nowString(): String = LocalDateTime.now().format(timeFormatter)

    private data class Metrics(
        val totalCount: Int,
        val actualCount: Int,
        val successCount: Int,
        val failCount: Int,
        val invalidCount: Int,
        val successRate: Double
    )

    private val activeTestingStatuses = setOf(
        DeviceUiStatus.CONNECTING,
        DeviceUiStatus.SUBSCRIBING,
        DeviceUiStatus.SENDING,
        DeviceUiStatus.WAITING_NOTIFY,
        DeviceUiStatus.DISCONNECTING,
        DeviceUiStatus.QUEUED
    )
}
