package com.faray.leproducttest.data.repository

import com.faray.leproducttest.common.AppErrorClassifier
import com.faray.leproducttest.data.auth.AuthApiClient
import com.faray.leproducttest.data.auth.AuthRemoteDataSource
import com.faray.leproducttest.data.auth.AuthTokenStorage
import com.faray.leproducttest.data.auth.AuthTokens
import com.faray.leproducttest.data.auth.StoredAuthTokens
import com.faray.leproducttest.domain.repository.AuthRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AuthRepositoryImpl(
    private val tokenStore: AuthTokenStorage,
    private val remoteDataSource: AuthRemoteDataSource = AuthApiClient
) : AuthRepository {

    private val refreshMutex = Mutex()

    override suspend fun login(username: String, password: String): Result<AuthTokens> {
        val result = remoteDataSource.login(username = username, password = password)
        result.onSuccess(tokenStore::save)
        result.onFailure { tokenStore.clear() }
        return result
    }

    override suspend fun getStoredTokens(): StoredAuthTokens? {
        return tokenStore.read()
    }

    override suspend fun getValidAccessToken(): Result<String> {
        val stored = tokenStore.read() ?: return Result.failure(
            IllegalStateException("Login expired. Please sign in again.")
        )
        val now = System.currentTimeMillis()
        if (stored.accessTokenExpiresAtMillis > now) {
            return Result.success(stored.accessToken)
        }
        if (stored.refreshTokenExpiresAtMillis <= now) {
            tokenStore.clear()
            return Result.failure(IllegalStateException("Login expired. Please sign in again."))
        }

        return refreshMutex.withLock {
            val latest = tokenStore.read() ?: return@withLock Result.failure(
                IllegalStateException("Login expired. Please sign in again.")
            )
            val currentTime = System.currentTimeMillis()
            if (latest.accessTokenExpiresAtMillis > currentTime) {
                return@withLock Result.success(latest.accessToken)
            }
            if (latest.refreshTokenExpiresAtMillis <= currentTime) {
                tokenStore.clear()
                return@withLock Result.failure(IllegalStateException("Login expired. Please sign in again."))
            }

            val refreshResult = remoteDataSource.refresh(latest.refreshToken)
            refreshResult.onSuccess(tokenStore::save)
            refreshResult.onFailure { error ->
                if (shouldClearTokens(error)) {
                    tokenStore.clear()
                }
            }
            refreshResult.map { it.accessToken }
        }
    }

    override suspend fun clearTokens() {
        tokenStore.clear()
    }

    private fun shouldClearTokens(error: Throwable): Boolean {
        return AppErrorClassifier.isAuthenticationError(
            AppErrorClassifier.fromThrowable(
                throwable = error,
                fallbackCode = com.faray.leproducttest.common.AppErrorCode.INTERNAL,
                fallbackMessage = error.message ?: "Unknown auth error"
            )
        )
    }
}
