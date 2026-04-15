package com.faray.leproducttest.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_config")
data class LocalConfigEntity(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: Long
)
