package com.faray.leproducttest.data.auth

import android.content.Context

class AuthTokenStore(context: Context) : AuthTokenStorage {

    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun save(tokens: AuthTokens) {
        preferences.edit()
            .putString(KEY_ACCESS_TOKEN, tokens.accessToken)
            .putLong(KEY_ACCESS_EXPIRES_AT, tokens.accessTokenExpiresAtMillis)
            .putString(KEY_REFRESH_TOKEN, tokens.refreshToken)
            .putLong(KEY_REFRESH_EXPIRES_AT, tokens.refreshTokenExpiresAtMillis)
            .apply()
    }

    override fun read(): StoredAuthTokens? {
        val accessToken = preferences.getString(KEY_ACCESS_TOKEN, null)
        val refreshToken = preferences.getString(KEY_REFRESH_TOKEN, null)
        if (accessToken.isNullOrBlank() || refreshToken.isNullOrBlank()) {
            return null
        }
        return StoredAuthTokens(
            accessToken = accessToken,
            accessTokenExpiresAtMillis = preferences.getLong(KEY_ACCESS_EXPIRES_AT, 0L),
            refreshToken = refreshToken,
            refreshTokenExpiresAtMillis = preferences.getLong(KEY_REFRESH_EXPIRES_AT, 0L)
        )
    }

    override fun clear() {
        preferences.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "auth_tokens"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_ACCESS_EXPIRES_AT = "access_token_expires_at"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_REFRESH_EXPIRES_AT = "refresh_token_expires_at"
    }
}

data class StoredAuthTokens(
    val accessToken: String,
    val accessTokenExpiresAtMillis: Long,
    val refreshToken: String,
    val refreshTokenExpiresAtMillis: Long
)

interface AuthTokenStorage {
    fun save(tokens: AuthTokens)
    fun read(): StoredAuthTokens?
    fun clear()
}
