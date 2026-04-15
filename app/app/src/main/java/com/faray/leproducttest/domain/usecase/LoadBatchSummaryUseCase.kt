package com.faray.leproducttest.domain.usecase

import com.faray.leproducttest.model.BatchProfile
import com.faray.leproducttest.domain.repository.BatchRepository

class LoadBatchSummaryUseCase(
    private val batchRepository: BatchRepository
) {
    suspend operator fun invoke(accessToken: String, batchId: String): Result<BatchProfile> {
        return batchRepository.fetchAndSaveBatchSummary(accessToken = accessToken, batchId = batchId)
    }
}
