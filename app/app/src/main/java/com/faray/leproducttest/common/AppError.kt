package com.faray.leproducttest.common

import no.nordicsemi.android.support.v18.scanner.ScanCallback

data class AppError(
    val code: AppErrorCode,
    val message: String
)

class AppException(
    val appError: AppError,
    cause: Throwable? = null
) : IllegalStateException(appError.message, cause)

enum class AppErrorCode {
    AUTH_REQUIRED,
    AUTH_REJECTED,
    NETWORK_TIMEOUT,
    NETWORK_UNAVAILABLE,
    RESPONSE_INVALID,
    REQUEST_INVALID,
    BATCH_NOT_FOUND,
    BLE_PERMISSION_REQUIRED,
    BLE_UNAVAILABLE,
    BLE_STATE_INVALID,
    BLE_SCAN_ALREADY_STARTED,
    BLE_SCAN_REGISTRATION_FAILED,
    BLE_SCAN_INTERNAL,
    BLE_SCAN_UNSUPPORTED,
    BLE_SCAN_NO_RESOURCES,
    BLE_SCAN_THROTTLED,
    INTERNAL
}

object AppErrorClassifier {

    fun fromThrowable(
        throwable: Throwable?,
        fallbackCode: AppErrorCode,
        fallbackMessage: String
    ): AppError {
        if (throwable is AppException) {
            return throwable.appError
        }
        val message = throwable?.message?.trim().orEmpty()
        if (message.isBlank()) {
            return AppError(fallbackCode, fallbackMessage)
        }
        val lowered = message.lowercase()
        val code = when {
            lowered.contains("token has expired") ||
                lowered.contains("login expired") ||
                lowered.contains("missing token") -> AppErrorCode.AUTH_REQUIRED
            lowered.contains("token has been revoked") ||
                lowered.contains("invalid token") ||
                lowered.contains("revoked") -> AppErrorCode.AUTH_REJECTED
            lowered.contains("timed out") || lowered.contains("超时") -> AppErrorCode.NETWORK_TIMEOUT
            lowered.contains("unable to connect") ||
                lowered.contains("failed to connect") ||
                lowered.contains("无法连接") -> AppErrorCode.NETWORK_UNAVAILABLE
            lowered.contains("invalid response") ||
                lowered.contains("response is invalid") ||
                lowered.contains("返回格式异常") -> AppErrorCode.RESPONSE_INVALID
            lowered.contains("payload is invalid") || lowered.contains("contains invalid") -> AppErrorCode.REQUEST_INVALID
            lowered.contains("not found on the server") -> AppErrorCode.BATCH_NOT_FOUND
            else -> fallbackCode
        }
        return AppError(code = code, message = message)
    }

    fun scanFailure(errorCode: Int?, exceptionMessage: String?): AppError {
        if (!exceptionMessage.isNullOrBlank()) {
            return AppError(
                code = AppErrorCode.BLE_SCAN_INTERNAL,
                message = "Failed to start BLE scan: $exceptionMessage"
            )
        }
        return when (errorCode) {
            ScanCallback.SCAN_FAILED_ALREADY_STARTED ->
                AppError(AppErrorCode.BLE_SCAN_ALREADY_STARTED, "BLE scan is already running")
            ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED ->
                AppError(AppErrorCode.BLE_SCAN_REGISTRATION_FAILED, "BLE scan registration failed. Toggle Bluetooth and try again.")
            ScanCallback.SCAN_FAILED_INTERNAL_ERROR ->
                AppError(AppErrorCode.BLE_SCAN_INTERNAL, "BLE scan hit an internal error. Toggle Bluetooth and try again.")
            ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED ->
                AppError(AppErrorCode.BLE_SCAN_UNSUPPORTED, "BLE scan is not supported on this device")
            ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES ->
                AppError(AppErrorCode.BLE_SCAN_NO_RESOURCES, "BLE scan failed: not enough hardware resources")
            ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY ->
                AppError(AppErrorCode.BLE_SCAN_THROTTLED, "BLE scan started too frequently. Wait a moment and try again.")
            else ->
                AppError(AppErrorCode.BLE_SCAN_INTERNAL, "BLE scan failed (${errorCode ?: "unknown"})")
        }
    }

    fun isAuthenticationError(error: AppError): Boolean {
        return error.code == AppErrorCode.AUTH_REQUIRED || error.code == AppErrorCode.AUTH_REJECTED
    }
}
