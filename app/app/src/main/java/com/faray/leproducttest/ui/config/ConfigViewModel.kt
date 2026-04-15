package com.faray.leproducttest.ui.config

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.faray.leproducttest.app.AppContainer
import com.faray.leproducttest.common.AppErrorClassifier
import com.faray.leproducttest.common.AppErrorCode
import com.faray.leproducttest.common.AppUiMessageResolver
import com.faray.leproducttest.data.ProductionRepository
import com.faray.leproducttest.ui.login.AuthUiState
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ConfigViewModel(application: Application) : AndroidViewModel(application) {

    private val container = AppContainer.from(application.applicationContext)
    private val messageResolver = AppUiMessageResolver(application::getString)
    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val uiStateData = MutableLiveData(ProductionRepository.configState.value ?: ConfigUiState())

    val uiState: LiveData<ConfigUiState> = uiStateData

    init {
        restoreFromLocalIfNeeded()
    }

    fun loadBatch(factoryId: String, batchId: String) {
        val cleanFactoryId = factoryId.trim()
        val cleanBatchId = batchId.trim()

        val validationError = validateInput(cleanFactoryId, cleanBatchId)
        if (validationError != null) {
            publishState(
                ConfigUiState(
                    factoryId = cleanFactoryId,
                    batchId = cleanBatchId,
                    errorMessage = validationError
                )
            )
            return
        }

        publishState(
            ConfigUiState(
                loading = true,
                importProgress = 15,
                factoryId = cleanFactoryId,
                batchId = cleanBatchId,
                summaryStatus = "Loading batch summary...",
                macListStatus = "Waiting for MAC list download",
                errorMessage = null
            )
        )

        viewModelScope.launch {
            container.localConfigRepository.saveFactoryId(cleanFactoryId)
            val accessTokenResult = container.authRepository.getValidAccessToken()
            if (accessTokenResult.isFailure) {
                val classified = AppErrorClassifier.fromThrowable(
                    throwable = accessTokenResult.exceptionOrNull(),
                    fallbackCode = AppErrorCode.AUTH_REQUIRED,
                    fallbackMessage = "Login expired. Please sign in again."
                )
                if (AppErrorClassifier.isAuthenticationError(classified)) {
                    ProductionRepository.adoptAuthState(AuthUiState(authenticated = false, errorMessage = classified.message))
                }
                Log.w(TAG, "config:access-token-failed code=${classified.code} batch=$cleanBatchId message=${classified.message}", accessTokenResult.exceptionOrNull())
                publishState(
                    ConfigUiState(
                        factoryId = cleanFactoryId,
                        batchId = cleanBatchId,
                        errorMessage = messageResolver.authRestoreError(accessTokenResult.exceptionOrNull())
                    )
                )
                return@launch
            }
            val accessToken = accessTokenResult.getOrThrow()

            val summaryResult = container.loadBatchSummaryUseCase(
                accessToken = accessToken,
                batchId = cleanBatchId
            )

            summaryResult.onFailure { throwable ->
                val classified = AppErrorClassifier.fromThrowable(
                    throwable = throwable,
                    fallbackCode = AppErrorCode.INTERNAL,
                    fallbackMessage = "Failed to load batch summary"
                )
                Log.e(TAG, "config:summary-failed code=${classified.code} batch=$cleanBatchId message=${classified.message}", throwable)
                publishState(
                    ConfigUiState(
                        factoryId = cleanFactoryId,
                        batchId = cleanBatchId,
                        summaryStatus = "Batch summary failed",
                        macListStatus = "Waiting for MAC list download",
                        errorMessage = messageResolver.batchSummaryError(throwable)
                    )
                )
                return@launch
            }

            val summaryProfile = summaryResult.getOrThrow()
            val batchProfile = summaryProfile.copy(
                bleConfig = summaryProfile.bleConfig.copy(
                    rssiMin = ConfigUiState.normalizeRssi(summaryProfile.bleConfig.rssiMin)
                )
            )
            container.localConfigRepository.saveLastBatchId(cleanBatchId)
            publishState(
                ConfigUiState(
                    loading = true,
                    importProgress = 55,
                    currentBatch = batchProfile,
                    factoryId = cleanFactoryId,
                    batchId = cleanBatchId,
                    summaryStatus = "Batch summary loaded",
                    macListStatus = "Downloading MAC list...",
                    errorMessage = null
                )
            )

            val macListResult = container.downloadAndImportMacListUseCase(
                accessToken = accessToken,
                batchId = cleanBatchId
            )

            macListResult.onFailure { throwable ->
                val classified = AppErrorClassifier.fromThrowable(
                    throwable = throwable,
                    fallbackCode = AppErrorCode.INTERNAL,
                    fallbackMessage = "Failed to download MAC list"
                )
                Log.e(TAG, "config:mac-list-failed code=${classified.code} batch=$cleanBatchId message=${classified.message}", throwable)
                publishState(
                    ConfigUiState(
                        currentBatch = batchProfile,
                        factoryId = cleanFactoryId,
                        batchId = cleanBatchId,
                        summaryStatus = "Batch summary loaded",
                        macListStatus = "MAC list download failed",
                        errorMessage = messageResolver.macListDownloadError(throwable)
                    )
                )
                return@launch
            }

            val importStats = macListResult.getOrThrow()
            publishState(
                ConfigUiState(
                    loading = false,
                    importProgress = 100,
                    currentBatch = batchProfile,
                    factoryId = cleanFactoryId,
                    batchId = cleanBatchId,
                    summaryStatus = "Batch summary loaded",
                    macListStatus = "MAC list imported",
                    importedCount = importStats.importedCount,
                    lastUpdatedAt = LocalDateTime.now().format(timeFormatter),
                    errorMessage = null,
                    canStartProduction = true
                )
            )
        }
    }

    fun updateRssi(rssi: Int) {
        val current = uiStateData.value ?: return
        val normalizedRssi = ConfigUiState.normalizeRssi(rssi)
        val batch = current.currentBatch ?: return
        if (batch.bleConfig.rssiMin == normalizedRssi) {
            return
        }
        val updatedBatch = batch.copy(
            bleConfig = batch.bleConfig.copy(rssiMin = normalizedRssi)
        )
        publishState(
            current.copy(
                currentBatch = updatedBatch
            )
        )
        viewModelScope.launch {
            container.batchRepository.updateBatchRssiMin(batchId = batch.batchId, rssiMin = normalizedRssi)
        }
    }

    private fun restoreFromLocalIfNeeded() {
        val current = uiStateData.value ?: ConfigUiState()
        if (current.loading || current.factoryId.isNotBlank() || current.batchId.isNotBlank() || current.currentBatch != null) {
            return
        }
        viewModelScope.launch {
            val restored = container.restoreConfigStateUseCase()
            if (restored.factoryId.isNotBlank() || restored.batchId.isNotBlank() || restored.currentBatch != null) {
                publishState(restored)
            }
        }
    }

    private fun publishState(state: ConfigUiState) {
        uiStateData.value = state
        ProductionRepository.adoptConfigState(state)
    }

    private fun validateInput(factoryId: String, batchId: String): String? {
        if (factoryId.isBlank()) {
            return messageResolver.factoryIdRequired()
        }
        if (batchId.isBlank()) {
            return messageResolver.batchIdRequired()
        }
        if (!SAFE_BATCH_ID.matches(batchId)) {
            return messageResolver.invalidBatchId()
        }
        return null
    }

    companion object {
        private val SAFE_BATCH_ID = Regex("^[A-Za-z0-9._-]+$")
        private const val TAG = "ConfigViewModel"
    }
}
