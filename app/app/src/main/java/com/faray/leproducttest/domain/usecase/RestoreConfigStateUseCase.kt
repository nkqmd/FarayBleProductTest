package com.faray.leproducttest.domain.usecase

import com.faray.leproducttest.domain.repository.BatchRepository
import com.faray.leproducttest.domain.repository.LocalConfigRepository
import com.faray.leproducttest.ui.config.ConfigUiState

class RestoreConfigStateUseCase(
    private val localConfigRepository: LocalConfigRepository,
    private val batchRepository: BatchRepository
) {
    suspend operator fun invoke(): ConfigUiState {
        val factoryId = localConfigRepository.getFactoryId().orEmpty()
        val batchId = localConfigRepository.getLastBatchId().orEmpty()
        if (batchId.isBlank()) {
            return ConfigUiState(factoryId = factoryId)
        }
        val profile = batchRepository.getBatchProfile(batchId)
        val importedCount = batchRepository.countImportedMacs(batchId)
        return ConfigUiState(
            currentBatch = profile,
            factoryId = factoryId,
            batchId = batchId,
            summaryStatus = if (profile == null) "Waiting for batch summary" else "Batch summary restored",
            macListStatus = if (importedCount > 0) "MAC list restored from local storage" else "Waiting for MAC list download",
            importedCount = importedCount,
            canStartProduction = profile != null && importedCount > 0
        )
    }
}
