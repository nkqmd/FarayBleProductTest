package com.faray.leproducttest.domain.usecase

import com.faray.leproducttest.model.ImportStats
import com.faray.leproducttest.domain.repository.BatchRepository

class DownloadAndImportMacListUseCase(
    private val batchRepository: BatchRepository
) {
    suspend operator fun invoke(accessToken: String, batchId: String): Result<ImportStats> {
        return batchRepository.downloadAndImportMacList(accessToken = accessToken, batchId = batchId)
    }
}
