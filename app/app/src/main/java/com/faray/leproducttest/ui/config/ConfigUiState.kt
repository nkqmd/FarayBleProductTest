package com.faray.leproducttest.ui.config

import com.faray.leproducttest.model.BatchProfile

data class ConfigUiState(
    val loading: Boolean = false,
    val importProgress: Int = 0,
    val currentBatch: BatchProfile? = null,
    val factoryId: String = "",
    val batchId: String = "",
    val summaryStatus: String = "Waiting for batch summary",
    val macListStatus: String = "Waiting for MAC list download",
    val importedCount: Int = 0,
    val lastUpdatedAt: String? = null,
    val errorMessage: String? = null,
    val canStartProduction: Boolean = false
) {
    companion object {
        const val RSSI_RANGE_FLOOR = -100
        const val RSSI_RANGE_CEIL = -30
        const val DEFAULT_RSSI = -70

        fun normalizeRssi(rssi: Int): Int {
            return rssi.coerceIn(RSSI_RANGE_FLOOR, RSSI_RANGE_CEIL)
        }
    }
}
