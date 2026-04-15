package com.faray.leproducttest.ui.shared

data class ShellUiState(
    val authenticated: Boolean = false,
    val batchId: String? = null,
    val factoryId: String? = null,
    val activeSessionId: String? = null,
    val navigationLocked: Boolean = false,
    val canStartProduction: Boolean = false
)
