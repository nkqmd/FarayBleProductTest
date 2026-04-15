package com.faray.leproducttest.ui.login

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.faray.leproducttest.app.AppContainer
import com.faray.leproducttest.common.AppErrorClassifier
import com.faray.leproducttest.common.AppErrorCode
import com.faray.leproducttest.common.AppUiMessageResolver
import com.faray.leproducttest.data.ProductionRepository
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val container = AppContainer.from(application.applicationContext)
    private val messageResolver = AppUiMessageResolver(application::getString)
    private val uiStateData = MutableLiveData(AuthUiState())

    val uiState: LiveData<AuthUiState> = uiStateData

    init {
        restoreSessionIfAvailable()
    }

    fun login(username: String, password: String) {
        val normalizedUsername = username.trim()
        if (normalizedUsername.isBlank() || password.isBlank()) {
            publishState(
                AuthUiState(
                    authenticated = false,
                    authenticating = false,
                    errorMessage = messageResolver.loginCredentialsRequired()
                )
            )
            return
        }

        publishState(
            uiStateData.value?.copy(
                authenticated = false,
                authenticating = true,
                errorMessage = null
            ) ?: AuthUiState(authenticating = true)
        )

        viewModelScope.launch {
            val result = container.loginUseCase(
                username = normalizedUsername,
                password = password
            )
            result.onSuccess { tokens ->
                Log.i(TAG, "auth:login-success accessExpiry=${tokens.accessTokenExpiresAtMillis} refreshExpiry=${tokens.refreshTokenExpiresAtMillis}")
                publishState(
                    AuthUiState(
                        authenticated = true,
                        authenticating = false,
                        accessTokenExpiresAt = tokens.accessTokenExpiresAtMillis,
                        refreshTokenExpiresAt = tokens.refreshTokenExpiresAtMillis,
                        refreshingToken = false,
                        errorMessage = null
                    )
                )
            }.onFailure { throwable ->
                container.authRepository.clearTokens()
                val classified = AppErrorClassifier.fromThrowable(
                    throwable = throwable,
                    fallbackCode = AppErrorCode.AUTH_REJECTED,
                    fallbackMessage = messageResolver.loginError(throwable)
                )
                Log.w(TAG, "auth:login-failed code=${classified.code} message=${classified.message}", throwable)
                publishState(
                    AuthUiState(
                        authenticated = false,
                        authenticating = false,
                        errorMessage = messageResolver.loginError(throwable)
                    )
                )
            }
        }
    }

    private fun restoreSessionIfAvailable() {
        viewModelScope.launch {
            val stored = container.authRepository.getStoredTokens()
            val now = System.currentTimeMillis()
            if (stored == null || stored.refreshTokenExpiresAtMillis <= now) {
                container.authRepository.clearTokens()
                publishState(
                    AuthUiState(
                        authenticated = false,
                        authenticating = false,
                        refreshingToken = false,
                        errorMessage = null
                    )
                )
                return@launch
            }

            if (stored.accessTokenExpiresAtMillis > now) {
                Log.i(TAG, "auth:restore-success mode=access-token")
                publishState(
                    AuthUiState(
                        authenticated = true,
                        authenticating = false,
                        accessTokenExpiresAt = stored.accessTokenExpiresAtMillis,
                        refreshTokenExpiresAt = stored.refreshTokenExpiresAtMillis,
                        refreshingToken = false,
                        errorMessage = null
                    )
                )
                return@launch
            }

            publishState(
                AuthUiState(
                    authenticated = false,
                    authenticating = false,
                    accessTokenExpiresAt = stored.accessTokenExpiresAtMillis,
                    refreshTokenExpiresAt = stored.refreshTokenExpiresAtMillis,
                    refreshingToken = true,
                    errorMessage = null
                )
            )

            val accessTokenResult = container.authRepository.getValidAccessToken()
            val refreshed = container.authRepository.getStoredTokens()
            val restoredState = if (accessTokenResult.isSuccess && refreshed != null) {
                Log.i(TAG, "auth:restore-success mode=refresh-token")
                AuthUiState(
                    authenticated = true,
                    authenticating = false,
                    accessTokenExpiresAt = refreshed.accessTokenExpiresAtMillis,
                    refreshTokenExpiresAt = refreshed.refreshTokenExpiresAtMillis,
                    refreshingToken = false,
                    errorMessage = null
                )
            } else {
                val classified = AppErrorClassifier.fromThrowable(
                    throwable = accessTokenResult.exceptionOrNull(),
                    fallbackCode = AppErrorCode.AUTH_REQUIRED,
                    fallbackMessage = messageResolver.authRestoreError(accessTokenResult.exceptionOrNull())
                )
                Log.w(TAG, "auth:restore-failed code=${classified.code} message=${classified.message}", accessTokenResult.exceptionOrNull())
                AuthUiState(
                    authenticated = false,
                    authenticating = false,
                    accessTokenExpiresAt = refreshed?.accessTokenExpiresAtMillis,
                    refreshTokenExpiresAt = refreshed?.refreshTokenExpiresAtMillis,
                    refreshingToken = false,
                    errorMessage = messageResolver.authRestoreError(accessTokenResult.exceptionOrNull())
                )
            }
            publishState(restoredState)
        }
    }

    private fun publishState(state: AuthUiState) {
        uiStateData.value = state
        ProductionRepository.adoptAuthState(state)
    }

    companion object {
        private const val TAG = "AuthViewModel"
    }
}
