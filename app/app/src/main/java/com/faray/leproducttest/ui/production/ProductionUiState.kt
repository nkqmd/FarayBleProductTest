package com.faray.leproducttest.ui.production

import com.faray.leproducttest.model.RuntimeDeviceItem
import com.faray.leproducttest.model.SessionStatus

data class ProductionUiState(
    val sessionStatus: SessionStatus? = null,
    val scanning: Boolean = false,
    val devices: List<RuntimeDeviceItem> = emptyList(),
    val queueSize: Int = 0,
    val runningCount: Int = 0,
    val maxConcurrent: Int = 1,
    val successCount: Int = 0,
    val failCount: Int = 0,
    val invalidCount: Int = 0,
    val totalCount: Int = 0,
    val actualCount: Int = 0,
    val successRate: Double = 0.0,
    val navigationLocked: Boolean = false,
    val errorMessage: String? = null,
    val filterPrefix: String? = null
)
