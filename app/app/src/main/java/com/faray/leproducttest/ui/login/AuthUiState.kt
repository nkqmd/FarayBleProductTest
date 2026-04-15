package com.faray.leproducttest.ui.login

data class AuthUiState(
    val authenticated: Boolean = false,
    val authenticating: Boolean = false,
    val accessTokenExpiresAt: Long? = null,
    val refreshTokenExpiresAt: Long? = null,
    val refreshingToken: Boolean = false,
    val errorMessage: String? = null
)
