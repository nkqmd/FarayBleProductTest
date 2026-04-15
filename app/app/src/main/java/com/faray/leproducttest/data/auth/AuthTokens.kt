package com.faray.leproducttest.data.auth

data class AuthTokens(
    val accessToken: String,
    val accessExpiresInSeconds: Long,
    val refreshToken: String,
    val refreshExpiresInSeconds: Long,
    val issuedAtMillis: Long
) {
    val accessTokenExpiresAtMillis: Long
        get() = issuedAtMillis + accessExpiresInSeconds * 1_000L

    val refreshTokenExpiresAtMillis: Long
        get() = issuedAtMillis + refreshExpiresInSeconds * 1_000L
}
