package com.faray.leproducttest.data.auth

interface AuthRemoteDataSource {
    suspend fun login(username: String, password: String): Result<AuthTokens>
    suspend fun refresh(refreshToken: String): Result<AuthTokens>
}
