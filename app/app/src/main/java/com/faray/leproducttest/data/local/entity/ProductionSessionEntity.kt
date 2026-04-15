package com.faray.leproducttest.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "production_session")
data class ProductionSessionEntity(
    @PrimaryKey val sessionId: String,
    val batchId: String,
    val factoryId: String,
    val startedAt: Long,
    val endedAt: Long?,
    val status: String
)
