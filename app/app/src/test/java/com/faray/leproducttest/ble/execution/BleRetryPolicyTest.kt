package com.faray.leproducttest.ble.execution

import com.faray.leproducttest.model.BleFailureReason
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleRetryPolicyTest {

    @Test
    fun retriesConnectSubscribeAndWriteFailuresBeforeLastAttempt() {
        assertTrue(BleRetryPolicy.shouldRetry(BleFailureReason.CONNECT_FAILED, attempt = 1))
        assertTrue(BleRetryPolicy.shouldRetry(BleFailureReason.SUBSCRIBE_FAILED, attempt = 2))
        assertTrue(BleRetryPolicy.shouldRetry(BleFailureReason.WRITE_FAILED, attempt = 1))
    }

    @Test
    fun doesNotRetryNonRetriableFailures() {
        assertFalse(BleRetryPolicy.shouldRetry(BleFailureReason.SERVICE_NOT_FOUND, attempt = 1))
        assertFalse(BleRetryPolicy.shouldRetry(BleFailureReason.CHARACTERISTIC_NOT_FOUND, attempt = 1))
        assertFalse(BleRetryPolicy.shouldRetry(BleFailureReason.NOTIFY_TIMEOUT, attempt = 1))
        assertFalse(BleRetryPolicy.shouldRetry(BleFailureReason.NOTIFY_MISMATCH, attempt = 1))
        assertFalse(BleRetryPolicy.shouldRetry(BleFailureReason.STOPPED_BY_SESSION, attempt = 1))
        assertFalse(BleRetryPolicy.shouldRetry(BleFailureReason.UNKNOWN, attempt = 1))
    }

    @Test
    fun doesNotRetryOnLastAttempt() {
        assertFalse(BleRetryPolicy.shouldRetry(BleFailureReason.CONNECT_FAILED, attempt = 3))
        assertFalse(BleRetryPolicy.shouldRetry(BleFailureReason.SUBSCRIBE_FAILED, attempt = 3))
        assertFalse(BleRetryPolicy.shouldRetry(BleFailureReason.WRITE_FAILED, attempt = 3))
    }
}
