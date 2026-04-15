package com.faray.leproducttest.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "upload_record",
    indices = [
        Index(value = ["sessionId", "uploadedAt"]),
        Index(value = ["batchId", "uploadedAt"])
    ]
)
data class UploadRecordEntity(
    @PrimaryKey val reportId: String,
    val sessionId: String,
    val batchId: String,
    val reportDigest: String,
    val uploadStatus: String,
    val uploadedAt: Long,
    val serverUploadId: String?,
    val duplicate: Boolean,
    val message: String?
)
