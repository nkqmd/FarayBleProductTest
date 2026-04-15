package com.faray.leproducttest.domain.usecase

import com.faray.leproducttest.data.auth.AuthTokens
import com.faray.leproducttest.domain.repository.AuthRepository

class LoginUseCase(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(username: String, password: String): Result<AuthTokens> {
        return authRepository.login(username = username, password = password)
    }
}
