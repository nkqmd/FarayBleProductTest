package com.faray.leproducttest.data.repository

import com.faray.leproducttest.common.AppError
import com.faray.leproducttest.common.AppErrorCode
import com.faray.leproducttest.common.AppException
import com.faray.leproducttest.data.auth.AuthRemoteDataSource
import com.faray.leproducttest.data.auth.AuthTokenStorage
import com.faray.leproducttest.data.auth.AuthTokens
import com.faray.leproducttest.data.auth.StoredAuthTokens
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AuthRepositoryImplTest {

    @Test
    fun refreshExpiredAccessTokenOnceForConcurrentRequests() = runBlocking {
        val now = System.currentTimeMillis()
        val tokenStore = FakeAuthTokenStore(
            stored = StoredAuthTokens(
                accessToken = "expired-access",
                accessTokenExpiresAtMillis = now - 5_000L,
                refreshToken = "refresh-1",
                refreshTokenExpiresAtMillis = now + 60_000L
            )
        )
        val remoteDataSource = FakeAuthRemoteDataSource(
            refreshedTokens = AuthTokens(
                accessToken = "fresh-access",
                accessExpiresInSeconds = 300,
                refreshToken = "refresh-2",
                refreshExpiresInSeconds = 600,
                issuedAtMillis = now
            ),
            refreshDelayMs = 50L
        )
        val repository = AuthRepositoryImpl(
            tokenStore = tokenStore,
            remoteDataSource = remoteDataSource
        )

        val first = async { repository.getValidAccessToken().getOrThrow() }
        val second = async { repository.getValidAccessToken().getOrThrow() }

        assertEquals("fresh-access", first.await())
        assertEquals("fresh-access", second.await())
        assertEquals(1, remoteDataSource.refreshCalls.get())
        assertEquals("fresh-access", tokenStore.read()?.accessToken)
        assertEquals("refresh-2", tokenStore.read()?.refreshToken)
    }

    @Test
    fun keepStoredTokensWhenRefreshFailsForTransientError() = runBlocking {
        val now = System.currentTimeMillis()
        val tokenStore = FakeAuthTokenStore(
            stored = StoredAuthTokens(
                accessToken = "expired-access",
                accessTokenExpiresAtMillis = now - 5_000L,
                refreshToken = "refresh-1",
                refreshTokenExpiresAtMillis = now + 60_000L
            )
        )
        val repository = AuthRepositoryImpl(
            tokenStore = tokenStore,
            remoteDataSource = FakeAuthRemoteDataSource(
                refreshFailure = IllegalStateException("Unable to connect to server")
            )
        )

        val result = repository.getValidAccessToken()

        assertTrue(result.isFailure)
        assertEquals("refresh-1", tokenStore.read()?.refreshToken)
    }

    @Test
    fun clearStoredTokensWhenRefreshTokenIsRejected() = runBlocking {
        val now = System.currentTimeMillis()
        val tokenStore = FakeAuthTokenStore(
            stored = StoredAuthTokens(
                accessToken = "expired-access",
                accessTokenExpiresAtMillis = now - 5_000L,
                refreshToken = "refresh-1",
                refreshTokenExpiresAtMillis = now + 60_000L
            )
        )
        val repository = AuthRepositoryImpl(
            tokenStore = tokenStore,
            remoteDataSource = FakeAuthRemoteDataSource(
                refreshFailure = AppException(
                    AppError(
                        code = AppErrorCode.AUTH_REQUIRED,
                        message = "Token has expired"
                    )
                )
            )
        )

        val result = repository.getValidAccessToken()

        assertTrue(result.isFailure)
        assertNull(tokenStore.read())
    }

    private class FakeAuthRemoteDataSource(
        private val refreshedTokens: AuthTokens? = null,
        private val refreshFailure: Throwable? = null,
        private val refreshDelayMs: Long = 0L
    ) : AuthRemoteDataSource {
        val refreshCalls = AtomicInteger(0)

        override suspend fun login(username: String, password: String): Result<AuthTokens> {
            throw UnsupportedOperationException()
        }

        override suspend fun refresh(refreshToken: String): Result<AuthTokens> {
            refreshCalls.incrementAndGet()
            if (refreshDelayMs > 0) {
                delay(refreshDelayMs)
            }
            return refreshFailure?.let { Result.failure(it) }
                ?: Result.success(requireNotNull(refreshedTokens))
        }
    }

    private class FakeAuthTokenStore(
        private var stored: StoredAuthTokens?
    ) : AuthTokenStorage {

        override fun read(): StoredAuthTokens? = stored

        override fun save(tokens: AuthTokens) {
            stored = StoredAuthTokens(
                accessToken = tokens.accessToken,
                accessTokenExpiresAtMillis = tokens.accessTokenExpiresAtMillis,
                refreshToken = tokens.refreshToken,
                refreshTokenExpiresAtMillis = tokens.refreshTokenExpiresAtMillis
            )
        }

        override fun clear() {
            stored = null
        }
    }
}
