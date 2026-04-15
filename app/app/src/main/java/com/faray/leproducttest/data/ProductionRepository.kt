package com.faray.leproducttest.data

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.faray.leproducttest.app.AppContainer
import com.faray.leproducttest.common.AppError
import com.faray.leproducttest.common.AppErrorClassifier
import com.faray.leproducttest.common.AppErrorCode
import com.faray.leproducttest.common.AppException
import com.faray.leproducttest.model.ProductionSession
import com.faray.leproducttest.model.QueueSnapshot
import com.faray.leproducttest.model.RuntimeDeviceItem
import com.faray.leproducttest.model.SessionStatistics
import com.faray.leproducttest.model.SessionStatus
import com.faray.leproducttest.domain.usecase.ValidateProductionStartUseCase
import com.faray.leproducttest.ui.config.ConfigUiState
import com.faray.leproducttest.ui.login.AuthUiState
import com.faray.leproducttest.ui.production.ProductionUiState
import com.faray.leproducttest.ui.result.ResultUiState
import com.faray.leproducttest.ui.shared.ShellUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Migration facade for existing UI wiring.
 *
 * New persistence and orchestration code should be added through repository/usecase modules first,
 * then delegated here only when old screens still depend on this object.
 */
object ProductionRepository {

    val requiredPermissions: Array<String>
        get() = ValidateProductionStartUseCase.requiredPermissions

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val compactFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

    private val configStateData = MutableLiveData(ConfigUiState())
    private val productionStateData = MutableLiveData(ProductionUiState())
    private val resultStateData = MutableLiveData(
        ResultUiState(
            uploadStatus = "Pending upload",
            uploadMessage = "Finish one production session before uploading the result"
        )
    )
    private val shellStateData = MutableLiveData(ShellUiState())

    val configState: LiveData<ConfigUiState> = configStateData
    val productionState: LiveData<ProductionUiState> = productionStateData
    val resultState: LiveData<ResultUiState> = resultStateData
    val shellState: LiveData<ShellUiState> = shellStateData

    private var container: AppContainer? = null
    private var runtimeStoreObserverRegistered = false
    private var queueObserverRegistered = false
    private var activeSessionId: String? = null
    private var activePrefix: String? = null
    private var staleTimeoutMs: Long = DEFAULT_STALE_TIMEOUT_MS

    fun bindContainer(appContainer: AppContainer) {
        container = appContainer
    }

    fun adoptConfigState(state: ConfigUiState) {
        configStateData.value = state
        shellStateData.value = (shellStateData.value ?: ShellUiState()).copy(
            batchId = state.batchId.ifBlank { null },
            factoryId = state.factoryId.ifBlank { null },
            canStartProduction = state.canStartProduction
        )

        val batch = state.currentBatch ?: return
        val currentProduction = productionStateData.value ?: ProductionUiState()
        if (currentProduction.sessionStatus != SessionStatus.RUNNING) {
            productionStateData.value = currentProduction.copy(
                maxConcurrent = batch.bleConfig.maxConcurrent,
                filterPrefix = batch.bleNamePrefix.ifBlank { null }
            )
        }
        resultStateData.value = (resultStateData.value ?: ResultUiState()).copy(
            batchId = batch.batchId,
            expectedCount = batch.expectedCount,
            uploadEnabled = false,
            uploadStatus = "Pending upload",
            uploadMessage = "Stop production before uploading the session result",
            uploadId = null,
            duplicate = false,
            batchUploadEnabled = false,
            batchUploading = false,
            batchUploadStatus = "Pending upload",
            batchUploadMessage = "Stop production before uploading the batch result",
            batchUploadId = null,
            batchUploadDuplicate = false
        )
    }

    fun adoptAuthState(state: AuthUiState) {
        shellStateData.value = (shellStateData.value ?: ShellUiState()).copy(
            authenticated = state.authenticated
        )
    }

    fun adoptRestoredResultState(resultState: ResultUiState, shellState: ShellUiState) {
        if (resultStateData.value?.uploading == true) {
            return
        }
        activeSessionId = shellState.activeSessionId
        resultStateData.value = resultState
        shellStateData.value = (shellStateData.value ?: ShellUiState()).copy(
            batchId = shellState.batchId ?: shellStateData.value?.batchId,
            factoryId = shellState.factoryId ?: shellStateData.value?.factoryId,
            activeSessionId = shellState.activeSessionId ?: shellStateData.value?.activeSessionId,
            navigationLocked = false,
            canStartProduction = shellStateData.value?.canStartProduction ?: false
        )
        val currentProduction = productionStateData.value ?: ProductionUiState()
        val restoredSessionStatus = when {
            resultState.uploadId != null -> SessionStatus.UPLOADED
            !resultState.sessionId.isNullOrBlank() -> SessionStatus.STOPPED
            else -> currentProduction.sessionStatus
        }
        productionStateData.value = currentProduction.copy(
            sessionStatus = restoredSessionStatus,
            scanning = false,
            navigationLocked = false,
            successCount = resultState.successCount,
            failCount = resultState.failCount,
            invalidCount = resultState.invalidCount,
            actualCount = resultState.actualCount,
            totalCount = resultState.actualCount + resultState.invalidCount,
            successRate = resultState.successRate
        )
    }

    fun onPermissionDenied() {
        publishError(
            AppError(
                code = AppErrorCode.BLE_PERMISSION_REQUIRED,
                message = "Bluetooth permissions are required before scanning"
            )
        )
    }

    fun startProduction(context: Context) {
        val currentState = productionStateData.value
        if (currentState?.sessionStatus == SessionStatus.RUNNING) {
            return
        }

        val config = configStateData.value ?: ConfigUiState()
        val appContainer = AppContainer.from(context)
        val validation = appContainer.validateProductionStartUseCase(context, config)
            .getOrElse { error ->
                val appError = if (error is AppException) {
                    error.appError
                } else {
                    AppErrorClassifier.fromThrowable(
                        throwable = error,
                        fallbackCode = AppErrorCode.INTERNAL,
                        fallbackMessage = "Failed to validate production start conditions"
                    )
                }
                publishError(appError)
                return
            }
        val batch = validation.batch
        val prefix = validation.prefix

        container = appContainer
        observeRuntimeStoreIfNeeded(appContainer)
        observeQueueStateIfNeeded(appContainer)
        val sessionId = "SESSION_${LocalDateTime.now().format(compactFormatter)}"
        activeSessionId = sessionId
        activePrefix = prefix
        staleTimeoutMs = validation.staleTimeoutMs

        productionStateData.value = ProductionUiState(
            sessionStatus = SessionStatus.RUNNING,
            scanning = true,
            devices = emptyList(),
            queueSize = 0,
            runningCount = 0,
            maxConcurrent = batch.bleConfig.maxConcurrent,
            successCount = 0,
            failCount = 0,
            invalidCount = 0,
            totalCount = 0,
            actualCount = 0,
            successRate = 0.0,
            navigationLocked = true,
            errorMessage = null,
            filterPrefix = prefix
        )
        resultStateData.value = ResultUiState(
            sessionId = sessionId,
            batchId = batch.batchId,
            expectedCount = batch.expectedCount,
            actualCount = 0,
            successCount = 0,
            failCount = 0,
            invalidCount = 0,
            successRate = 0.0,
            uploadEnabled = false,
            uploading = false,
            uploadStatus = "Pending upload",
            uploadMessage = "Stop production before uploading the session result",
            uploadId = null,
            duplicate = false,
            batchUploadEnabled = false,
            batchUploading = false,
            batchUploadStatus = "Pending upload",
            batchUploadMessage = "Stop production before uploading the batch result",
            batchUploadId = null,
            batchUploadDuplicate = false
        )
        shellStateData.value = (shellStateData.value ?: ShellUiState()).copy(
            batchId = batch.batchId,
            factoryId = config.factoryId.ifBlank { null },
            activeSessionId = sessionId,
            navigationLocked = true,
            canStartProduction = true
        )
        Log.d(
            TAG,
            "session:start session=$sessionId batch=${batch.batchId} factory=${config.factoryId} " +
                "prefix=$prefix maxConcurrent=${batch.bleConfig.maxConcurrent}"
        )

        repositoryScope.launch {
            appContainer.productionSessionCoordinator.startSession(
                ProductionSession(
                    sessionId = sessionId,
                    batchId = batch.batchId,
                    factoryId = config.factoryId,
                    startedAt = System.currentTimeMillis(),
                    status = SessionStatus.RUNNING
                ),
                profile = batch,
                staleTimeoutMs = staleTimeoutMs,
                onScanError = { errorCode ->
                    publishError(AppErrorClassifier.scanFailure(errorCode, null))
                }
            )
        }
    }

    fun stopProduction() {
        activePrefix = null
        val current = productionStateData.value ?: ProductionUiState()
        val sessionId = activeSessionId
        if (current.sessionStatus != SessionStatus.RUNNING && current.sessionStatus != SessionStatus.STOPPING) {
            return
        }
        Log.d(TAG, "session:stop-request session=$sessionId")
        productionStateData.value = current.copy(
            sessionStatus = SessionStatus.STOPPING,
            scanning = false,
            navigationLocked = true,
            errorMessage = null
        )
        shellStateData.value = (shellStateData.value ?: ShellUiState()).copy(navigationLocked = true)

        repositoryScope.launch {
            val appContainer = container
            appContainer?.productionSessionCoordinator?.stopSession(
                sessionId = sessionId,
                endedAt = System.currentTimeMillis()
            )
            val latestSummary = if (!sessionId.isNullOrBlank()) {
                loadSessionSummary(appContainer = appContainer, sessionId = sessionId)
            } else {
                null
            }

            val latest = productionStateData.value ?: ProductionUiState()
            resultStateData.value = (resultStateData.value ?: ResultUiState()).copy(
                sessionId = sessionId,
                batchId = shellStateData.value?.batchId,
                expectedCount = latestSummary?.expectedCount ?: (resultStateData.value?.expectedCount ?: 0),
                actualCount = latestSummary?.actualCount ?: latest.actualCount,
                successCount = latestSummary?.successCount ?: latest.successCount,
                failCount = latestSummary?.failCount ?: latest.failCount,
                invalidCount = latestSummary?.invalidCount ?: latest.invalidCount,
                successRate = latestSummary?.successRate ?: latest.successRate,
                uploadEnabled = !sessionId.isNullOrBlank(),
                uploading = false,
                uploadStatus = "Pending upload",
                uploadMessage = "Upload is available for the latest stopped session",
                uploadId = null,
                duplicate = false,
                batchUploadEnabled = !sessionId.isNullOrBlank(),
                batchUploading = false,
                batchUploadStatus = "Pending upload",
                batchUploadMessage = "Upload is available for the current batch cumulative result",
                batchUploadId = null,
                batchUploadDuplicate = false
            )
            shellStateData.value = (shellStateData.value ?: ShellUiState()).copy(
                activeSessionId = sessionId,
                navigationLocked = false
            )
            productionStateData.value = latest.copy(
                sessionStatus = SessionStatus.STOPPED,
                scanning = false,
                queueSize = 0,
                runningCount = 0,
                navigationLocked = false
            )
        }
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

        repositoryScope.launch {
            val appContainer = container ?: run {
                val error = AppError(AppErrorCode.INTERNAL, "App container is not ready")
                resultStateData.value = current.copy(
                    uploading = false,
                    uploadStatus = "Upload failed",
                    uploadMessage = error.message
                )
                Log.e(TAG, "upload:error code=${error.code} session=${current.sessionId} message=${error.message}")
                return@launch
            }
            val requestResult = appContainer.buildSessionReportUseCase(current.sessionId)
            val request = requestResult.getOrElse { error ->
                val classified = AppErrorClassifier.fromThrowable(
                    throwable = error,
                    fallbackCode = AppErrorCode.INTERNAL,
                    fallbackMessage = "Failed to build report payload"
                )
                resultStateData.value = current.copy(
                    uploading = false,
                    uploadStatus = "Upload failed",
                    uploadMessage = classified.message
                )
                Log.e(TAG, "upload:build-failed code=${classified.code} session=${current.sessionId} message=${classified.message}", error)
                return@launch
            }
            val uploadResult = appContainer.uploadSessionReportUseCase(current.sessionId, request)
            val uploadRecord = uploadResult.getOrElse { error ->
                val classified = AppErrorClassifier.fromThrowable(
                    throwable = error,
                    fallbackCode = AppErrorCode.INTERNAL,
                    fallbackMessage = "Failed to upload session result"
                )
                if (AppErrorClassifier.isAuthenticationError(classified)) {
                    adoptAuthState(AuthUiState(authenticated = false, errorMessage = classified.message))
                }
                resultStateData.value = current.copy(
                    uploading = false,
                    uploadEnabled = true,
                    uploadStatus = "Upload failed",
                    uploadMessage = classified.message
                )
                Log.e(TAG, "upload:request-failed code=${classified.code} session=${current.sessionId} message=${classified.message}", error)
                return@launch
            }
            val refreshedSummary = loadSessionSummary(appContainer = appContainer, sessionId = current.sessionId)
            resultStateData.value = current.copy(
                expectedCount = refreshedSummary?.expectedCount ?: current.expectedCount,
                actualCount = refreshedSummary?.actualCount ?: current.actualCount,
                successCount = refreshedSummary?.successCount ?: current.successCount,
                failCount = refreshedSummary?.failCount ?: current.failCount,
                invalidCount = refreshedSummary?.invalidCount ?: current.invalidCount,
                successRate = refreshedSummary?.successRate ?: current.successRate,
                uploading = false,
                uploadEnabled = true,
                uploadStatus = if (uploadRecord.duplicate) "Duplicate upload" else "Upload success",
                uploadMessage = uploadRecord.message ?: if (uploadRecord.duplicate) {
                    "The server already has this report"
                } else {
                    "The session result has been uploaded"
                },
                uploadId = uploadRecord.uploadId,
                duplicate = uploadRecord.duplicate
            )
            val production = productionStateData.value
            if (production?.sessionStatus == SessionStatus.STOPPED) {
                productionStateData.value = production.copy(sessionStatus = SessionStatus.UPLOADED)
            }
            Log.i(
                TAG,
                "upload:success session=${current.sessionId} duplicate=${uploadRecord.duplicate} uploadId=${uploadRecord.uploadId ?: "-"}"
            )
        }
    }

    fun uploadBatchResult() {
        val current = resultStateData.value ?: return
        val batchId = current.batchId ?: shellStateData.value?.batchId
        val factoryId = shellStateData.value?.factoryId
        val sessionStatus = productionStateData.value?.sessionStatus
        if (!current.batchUploadEnabled || batchId.isNullOrBlank() || factoryId.isNullOrBlank()) {
            return
        }
        if (sessionStatus == SessionStatus.RUNNING || sessionStatus == SessionStatus.STOPPING) {
            resultStateData.value = current.copy(
                batchUploading = false,
                batchUploadStatus = "Pending upload",
                batchUploadMessage = "Stop production before uploading the batch result"
            )
            return
        }

        resultStateData.value = current.copy(
            batchUploading = true,
            batchUploadStatus = "Uploading",
            batchUploadMessage = "Building batch_result.json"
        )

        repositoryScope.launch {
            val appContainer = container ?: run {
                val error = AppError(AppErrorCode.INTERNAL, "App container is not ready")
                resultStateData.value = current.copy(
                    batchUploading = false,
                    batchUploadStatus = "Upload failed",
                    batchUploadMessage = error.message
                )
                return@launch
            }
            val requestResult = appContainer.buildBatchReportUseCase(batchId = batchId, factoryId = factoryId)
            val request = requestResult.getOrElse { error ->
                val classified = AppErrorClassifier.fromThrowable(
                    throwable = error,
                    fallbackCode = AppErrorCode.INTERNAL,
                    fallbackMessage = "Failed to build batch_result.json"
                )
                resultStateData.value = current.copy(
                    batchUploading = false,
                    batchUploadEnabled = true,
                    batchUploadStatus = "Upload failed",
                    batchUploadMessage = classified.message
                )
                Log.e(
                    TAG,
                    "batch-upload:build-failed code=${classified.code} batch=$batchId message=${classified.message}",
                    error
                )
                return@launch
            }
            val uploadResult = appContainer.uploadBatchReportUseCase(request)
            val uploadRecord = uploadResult.getOrElse { error ->
                val classified = AppErrorClassifier.fromThrowable(
                    throwable = error,
                    fallbackCode = AppErrorCode.INTERNAL,
                    fallbackMessage = "Failed to upload batch result"
                )
                if (AppErrorClassifier.isAuthenticationError(classified)) {
                    adoptAuthState(AuthUiState(authenticated = false, errorMessage = classified.message))
                }
                resultStateData.value = current.copy(
                    batchUploading = false,
                    batchUploadEnabled = true,
                    batchUploadStatus = "Upload failed",
                    batchUploadMessage = classified.message
                )
                Log.e(
                    TAG,
                    "batch-upload:request-failed code=${classified.code} batch=$batchId message=${classified.message}",
                    error
                )
                return@launch
            }
            resultStateData.value = current.copy(
                batchUploading = false,
                batchUploadEnabled = true,
                batchUploadStatus = if (uploadRecord.duplicate) "Duplicate upload" else "Upload success",
                batchUploadMessage = uploadRecord.message ?: if (uploadRecord.duplicate) {
                    "The server already has this batch result"
                } else {
                    "The batch_result.json file has been uploaded"
                },
                batchUploadId = uploadRecord.uploadId,
                batchUploadDuplicate = uploadRecord.duplicate
            )
            Log.i(
                TAG,
                "batch-upload:success batch=$batchId duplicate=${uploadRecord.duplicate} uploadId=${uploadRecord.uploadId ?: "-"}"
            )
        }
    }

    private fun publishRuntimeDevices(devices: List<RuntimeDeviceItem>) {
        val current = productionStateData.value ?: ProductionUiState()
        productionStateData.value = current.copy(
            devices = devices,
            totalCount = maxOf(devices.size, current.totalCount),
            errorMessage = null
        )
        refreshSessionStatistics(activeSessionId)
    }

    private fun publishError(error: AppError) {
        Log.e(TAG, "session:error code=${error.code} session=$activeSessionId message=${error.message}")
        repositoryScope.launch {
            container?.productionSessionCoordinator?.stopSession(
                sessionId = activeSessionId,
                endedAt = System.currentTimeMillis()
            )
        }
        repositoryScope.launch {
            val current = productionStateData.value ?: ProductionUiState()
            productionStateData.value = current.copy(
                sessionStatus = if (current.sessionStatus == SessionStatus.RUNNING || current.sessionStatus == SessionStatus.STOPPING) {
                    SessionStatus.STOPPED
                } else {
                    current.sessionStatus
                },
                scanning = false,
                navigationLocked = false,
                errorMessage = error.message
            )
            shellStateData.value = (shellStateData.value ?: ShellUiState()).copy(
                navigationLocked = false
            )
            resultStateData.value = (resultStateData.value ?: ResultUiState()).copy(
                uploadEnabled = !activeSessionId.isNullOrBlank(),
                uploading = false,
                batchUploadEnabled = false,
                batchUploading = false
            )
        }
    }

    private suspend fun loadSessionSummary(
        appContainer: AppContainer?,
        sessionId: String
    ): SessionSummary? {
        val session = appContainer?.sessionRepository?.getSession(sessionId) ?: return null
        val statistics = appContainer.testRecordRepository.getSessionStatistics(sessionId)
        val expectedCount = appContainer.batchRepository.getBatchProfile(session.batchId)?.expectedCount ?: 0
        return SessionSummary(
            expectedCount = expectedCount,
            actualCount = statistics.actualCount,
            successCount = statistics.successCount,
            failCount = statistics.failCount,
            invalidCount = statistics.invalidCount,
            successRate = statistics.successRate
        )
    }

    private fun refreshSessionStatistics(sessionId: String?) {
        if (sessionId.isNullOrBlank()) {
            return
        }
        repositoryScope.launch {
            val appContainer = container ?: return@launch
            val statistics = appContainer.testRecordRepository.getSessionStatistics(sessionId)
            applySessionStatistics(
                sessionId = sessionId,
                statistics = statistics
            )
        }
    }

    private fun applySessionStatistics(
        sessionId: String,
        statistics: SessionStatistics
    ) {
        val currentProduction = productionStateData.value ?: ProductionUiState()
        val visibleTotal = currentProduction.devices.size
        val sessionTotal = statistics.actualCount + statistics.invalidCount
        productionStateData.value = currentProduction.copy(
            totalCount = maxOf(visibleTotal, sessionTotal),
            actualCount = statistics.actualCount,
            successCount = statistics.successCount,
            failCount = statistics.failCount,
            invalidCount = statistics.invalidCount,
            successRate = statistics.successRate
        )
        val currentResult = resultStateData.value ?: ResultUiState()
        resultStateData.value = currentResult.copy(
            sessionId = sessionId,
            batchId = currentResult.batchId ?: shellStateData.value?.batchId,
            actualCount = statistics.actualCount,
            successCount = statistics.successCount,
            failCount = statistics.failCount,
            invalidCount = statistics.invalidCount,
            successRate = statistics.successRate
        )
    }

    private data class SessionSummary(
        val expectedCount: Int,
        val actualCount: Int,
        val successCount: Int,
        val failCount: Int,
        val invalidCount: Int,
        val successRate: Double
    )

    private fun observeRuntimeStoreIfNeeded(appContainer: AppContainer) {
        if (runtimeStoreObserverRegistered) {
            return
        }
        runtimeStoreObserverRegistered = true
        appContainer.runtimeDeviceStore.observeVisibleDevices().observeForever(runtimeDevicesObserver)
    }

    private fun observeQueueStateIfNeeded(appContainer: AppContainer) {
        if (queueObserverRegistered) {
            return
        }
        queueObserverRegistered = true
        appContainer.productionStateMachine.observeQueueState().observeForever(queueObserver)
    }

    private val runtimeDevicesObserver = Observer<List<RuntimeDeviceItem>> { devices ->
        publishRuntimeDevices(devices)
    }

    private val queueObserver = Observer<QueueSnapshot> { snapshot ->
        val current = productionStateData.value ?: ProductionUiState()
        productionStateData.value = current.copy(
            queueSize = snapshot.queuedCount,
            runningCount = snapshot.runningCount,
            maxConcurrent = snapshot.maxConcurrent
        )
    }

    private const val DEFAULT_STALE_TIMEOUT_MS = 300L
    private const val TAG = "ProductionRepository"
}
