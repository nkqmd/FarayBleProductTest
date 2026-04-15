package com.faray.leproducttest.domain.repository

import com.faray.leproducttest.data.auth.AuthTokens
import com.faray.leproducttest.data.auth.StoredAuthTokens

interface AuthRepository {
    suspend fun login(username: String, password: String): Result<AuthTokens>
    suspend fun getStoredTokens(): StoredAuthTokens?
    suspend fun getValidAccessToken(): Result<String>
    suspend fun clearTokens()
}
