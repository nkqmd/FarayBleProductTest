package com.faray.leproducttest.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "batch_mac",
    primaryKeys = ["batchId", "macValue"],
    indices = [Index(value = ["batchId", "macValue"], unique = true)]
)
data class BatchMacEntity(
    val batchId: String,
    val macValue: Long,
    val importedAt: Long
)
