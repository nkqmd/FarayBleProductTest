package com.faray.leproducttest.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppErrorClassifierTest {

    @Test
    fun classifyAuthExpiredMessage() {
        val error = AppErrorClassifier.fromThrowable(
            throwable = IllegalStateException("Token has expired"),
            fallbackCode = AppErrorCode.INTERNAL,
            fallbackMessage = "fallback"
        )

        assertEquals(AppErrorCode.AUTH_REQUIRED, error.code)
        assertTrue(AppErrorClassifier.isAuthenticationError(error))
    }

    @Test
    fun classifyTimeoutMessage() {
        val error = AppErrorClassifier.fromThrowable(
            throwable = IllegalStateException("Timed out while uploading production result"),
            fallbackCode = AppErrorCode.INTERNAL,
            fallbackMessage = "fallback"
        )

        assertEquals(AppErrorCode.NETWORK_TIMEOUT, error.code)
    }

    @Test
    fun unwrapStructuredAppException() {
        val error = AppErrorClassifier.fromThrowable(
            throwable = AppException(
                AppError(
                    code = AppErrorCode.BATCH_NOT_FOUND,
                    message = "Batch files were not found on the server."
                )
            ),
            fallbackCode = AppErrorCode.INTERNAL,
            fallbackMessage = "fallback"
        )

        assertEquals(AppErrorCode.BATCH_NOT_FOUND, error.code)
        assertEquals("Batch files were not found on the server.", error.message)
    }
}
