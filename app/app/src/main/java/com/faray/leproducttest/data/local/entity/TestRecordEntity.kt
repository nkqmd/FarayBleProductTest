package com.faray.leproducttest.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "test_record",
    primaryKeys = ["sessionId", "parsedMac"],
    indices = [
        Index(value = ["batchId", "parsedMac", "finalStatus"]),
        Index(value = ["sessionId", "finalStatus"])
    ]
)
data class TestRecordEntity(
    val sessionId: String,
    val batchId: String,
    val parsedMac: Long,
    val deviceAddress: String,
    val advName: String,
    val rssi: Int,
    val finalStatus: String,
    val success: Boolean,
    val reason: String?,
    val startedAt: Long,
    val endedAt: Long,
    val createdAt: Long
)
