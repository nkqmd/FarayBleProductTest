package com.faray.leproducttest.ble.execution

import com.faray.leproducttest.model.BleFailureReason

object BleRetryPolicy {

    const val FIXED_MAX_ATTEMPTS = 3

    fun shouldRetry(
        reason: BleFailureReason,
        attempt: Int,
        maxAttempts: Int = FIXED_MAX_ATTEMPTS
    ): Boolean {
        if (attempt >= maxAttempts) {
            return false
        }
        return when (reason) {
            BleFailureReason.CONNECT_FAILED,
            BleFailureReason.SUBSCRIBE_FAILED,
            BleFailureReason.WRITE_FAILED -> true
            else -> false
        }
    }
}
