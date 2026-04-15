package com.faray.leproducttest.data.repository

import androidx.room.withTransaction
import com.faray.leproducttest.common.MacAddressCodec
import com.faray.leproducttest.data.batch.BatchApiClient
import com.faray.leproducttest.data.batch.BleConfigNormalizer
import com.faray.leproducttest.data.batch.BatchFileStore
import com.faray.leproducttest.data.local.AppDatabase
import com.faray.leproducttest.data.local.dao.BatchMacDao
import com.faray.leproducttest.data.local.dao.BatchProfileDao
import com.faray.leproducttest.data.local.entity.BatchMacEntity
import com.faray.leproducttest.data.local.entity.BatchProfileEntity
import com.faray.leproducttest.domain.repository.BatchRepository
import com.faray.leproducttest.domain.service.WhitelistMatcher
import com.faray.leproducttest.model.BatchProfile
import com.faray.leproducttest.model.BleTestPlan
import com.faray.leproducttest.model.ImportStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class BatchRepositoryImpl(
    private val database: AppDatabase,
    private val batchProfileDao: BatchProfileDao,
    private val batchMacDao: BatchMacDao,
    private val batchFileStore: BatchFileStore,
    private val whitelistMatcher: WhitelistMatcher
) : BatchRepository {

    override suspend fun fetchAndSaveBatchSummary(accessToken: String, batchId: String): Result<BatchProfile> {
        val result = BatchApiClient.fetchSummary(accessToken = accessToken, batchId = batchId)
        result.onSuccess { profile ->
            batchProfileDao.insert(profile.toEntity(savedAt = System.currentTimeMillis()))
        }
        return result
    }

    override suspend fun downloadAndImportMacList(accessToken: String, batchId: String): Result<ImportStats> {
        val startedAt = System.currentTimeMillis()
        val downloadResult = BatchApiClient.downloadMacList(
            accessToken = accessToken,
            batchId = batchId,
            destinationFile = batchFileStore.macListFile(batchId)
        )
        return downloadResult.mapCatching { downloaded ->
            importMacFile(batchId = batchId, file = downloaded.file, startedAt = startedAt)
                .also { whitelistMatcher.prepare(batchId) }
        }
    }

    override suspend fun getBatchProfile(batchId: String): BatchProfile? {
        return batchProfileDao.getByBatchId(batchId)?.toDomain()
    }

    override suspend fun updateBatchRssiMin(batchId: String, rssiMin: Int) {
        batchProfileDao.updateRssiMin(batchId = batchId, rssiMin = rssiMin)
    }

    override suspend fun countImportedMacs(batchId: String): Int {
        return batchMacDao.countByBatchId(batchId)
    }

    override suspend fun isMacWhitelisted(batchId: String, macValue: Long): Boolean {
        return whitelistMatcher.contains(batchId = batchId, macValue = macValue)
    }

    private suspend fun importMacFile(batchId: String, file: File, startedAt: Long): ImportStats {
        return withContext(Dispatchers.IO) {
            val importedAt = System.currentTimeMillis()
            var importedCount = 0
            var skippedCount = 0

            database.withTransaction {
                batchMacDao.deleteByBatchId(batchId)
                val buffer = ArrayList<BatchMacEntity>(CHUNK_SIZE)

                file.bufferedReader().useLines { lines ->
                    lines.forEach { rawLine ->
                        val parsed = MacAddressCodec.parseToLong(rawLine)
                        if (parsed == null) {
                            if (rawLine.isNotBlank()) {
                                skippedCount += 1
                            }
                            return@forEach
                        }
                        buffer += BatchMacEntity(
                            batchId = batchId,
                            macValue = parsed,
                            importedAt = importedAt
                        )
                        if (buffer.size >= CHUNK_SIZE) {
                            importedCount += flushBuffer(buffer)
                        }
                    }
                }
                if (buffer.isNotEmpty()) {
                    importedCount += flushBuffer(buffer)
                }
            }

            ImportStats(
                batchId = batchId,
                importedCount = importedCount,
                skippedCount = skippedCount,
                startedAt = startedAt,
                endedAt = System.currentTimeMillis()
            )
        }
    }

    private suspend fun flushBuffer(buffer: MutableList<BatchMacEntity>): Int {
        val inserted = batchMacDao.insertAll(buffer).count { it != -1L }
        buffer.clear()
        return inserted
    }

    private fun BatchProfile.toEntity(savedAt: Long): BatchProfileEntity {
        return BatchProfileEntity(
            batchId = batchId,
            expectedCount = expectedCount,
            expireTime = expireTime,
            expectedFirmware = expectedFirmware,
            deviceType = deviceType,
            bleNamePrefix = bleNamePrefix,
            bleNameRule = bleNameRule,
            rawBleConfigJson = rawBleConfigJson,
            macListCount = macListCount,
            macListHash = macListHash,
            macListVersion = macListVersion,
            macListUrl = macListUrl,
            rssiMin = bleConfig.rssiMin,
            rssiMax = bleConfig.rssiMax,
            scanIdleMs = bleConfig.scanIdleMs,
            scanActiveMs = bleConfig.scanActiveMs,
            maxConcurrent = bleConfig.maxConcurrent,
            connectTimeoutMs = bleConfig.connectTimeoutMs,
            notifyTimeoutMs = bleConfig.notifyTimeoutMs,
            overallTimeoutMs = bleConfig.overallTimeoutMs,
            serviceUuid = bleConfig.serviceUuid,
            notifyCharacteristicUuid = bleConfig.notifyCharacteristicUuid,
            writeCharacteristicUuid = bleConfig.writeCharacteristicUuid,
            writePayloadHex = bleConfig.writePayloadHex,
            expectedNotifyValueHex = bleConfig.expectedNotifyValueHex,
            savedAt = savedAt
        )
    }

    private fun BatchProfileEntity.toDomain(): BatchProfile {
        val normalizedPlan = runCatching {
            BleConfigNormalizer.fromJson(rawBleConfigJson)
        }.getOrElse {
            BleTestPlan(
                rssiMin = rssiMin,
                rssiMax = rssiMax,
                scanIdleMs = scanIdleMs,
                scanActiveMs = scanActiveMs,
                maxConcurrent = maxConcurrent,
                connectTimeoutMs = connectTimeoutMs,
                notifyTimeoutMs = notifyTimeoutMs,
                overallTimeoutMs = overallTimeoutMs,
                serviceUuid = serviceUuid,
                notifyCharacteristicUuid = notifyCharacteristicUuid,
                writeCharacteristicUuid = writeCharacteristicUuid,
                writePayloadHex = writePayloadHex,
                expectedNotifyValueHex = expectedNotifyValueHex
            )
        }.copy(
            rssiMin = rssiMin,
            rssiMax = rssiMax
        )
        return BatchProfile(
            batchId = batchId,
            expectedCount = expectedCount,
            expireTime = expireTime,
            expectedFirmware = expectedFirmware,
            deviceType = deviceType,
            bleNamePrefix = bleNamePrefix,
            bleNameRule = bleNameRule,
            bleConfig = normalizedPlan,
            rawBleConfigJson = rawBleConfigJson,
            macListCount = macListCount,
            macListHash = macListHash,
            macListVersion = macListVersion,
            macListUrl = macListUrl
        )
    }

    private companion object {
        const val CHUNK_SIZE = 1000
    }
}
