package com.faray.leproducttest.ui.result

data class ResultUiState(
    val sessionId: String? = null,
    val batchId: String? = null,
    val expectedCount: Int = 0,
    val actualCount: Int = 0,
    val successCount: Int = 0,
    val failCount: Int = 0,
    val invalidCount: Int = 0,
    val successRate: Double = 0.0,
    val uploadEnabled: Boolean = false,
    val uploading: Boolean = false,
    val uploadStatus: String? = null,
    val uploadMessage: String? = null,
    val uploadId: String? = null,
    val duplicate: Boolean = false,
    val batchUploadEnabled: Boolean = false,
    val batchUploading: Boolean = false,
    val batchUploadStatus: String? = null,
    val batchUploadMessage: String? = null,
    val batchUploadId: String? = null,
    val batchUploadDuplicate: Boolean = false
)
