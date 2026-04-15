package com.faray.leproducttest.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "batch_profile")
data class BatchProfileEntity(
    @PrimaryKey val batchId: String,
    val expectedCount: Int,
    val expireTime: String,
    val expectedFirmware: String,
    val deviceType: String,
    val bleNamePrefix: String,
    val bleNameRule: String?,
    val rawBleConfigJson: String,
    val macListCount: Int,
    val macListHash: String,
    val macListVersion: String,
    val macListUrl: String,
    val rssiMin: Int,
    val rssiMax: Int?,
    val scanIdleMs: Long,
    val scanActiveMs: Long,
    val maxConcurrent: Int,
    val connectTimeoutMs: Long,
    val notifyTimeoutMs: Long,
    val overallTimeoutMs: Long,
    val serviceUuid: String,
    val notifyCharacteristicUuid: String,
    val writeCharacteristicUuid: String,
    val writePayloadHex: String,
    val expectedNotifyValueHex: String?,
    val savedAt: Long
)
