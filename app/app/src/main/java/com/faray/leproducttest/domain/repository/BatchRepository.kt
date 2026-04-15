package com.faray.leproducttest.domain.repository

import com.faray.leproducttest.model.BatchProfile
import com.faray.leproducttest.model.ImportStats

interface BatchRepository {
    suspend fun fetchAndSaveBatchSummary(accessToken: String, batchId: String): Result<BatchProfile>
    suspend fun downloadAndImportMacList(accessToken: String, batchId: String): Result<ImportStats>
    suspend fun getBatchProfile(batchId: String): BatchProfile?
    suspend fun updateBatchRssiMin(batchId: String, rssiMin: Int)
    suspend fun countImportedMacs(batchId: String): Int
    suspend fun isMacWhitelisted(batchId: String, macValue: Long): Boolean
}
