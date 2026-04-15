package com.faray.leproducttest.data.batch

import com.faray.leproducttest.model.BleTestPlan
import org.json.JSONObject
import java.util.UUID
import kotlin.math.max

object BleConfigNormalizer {

    fun normalize(raw: JSONObject): BleTestPlan {
        val timeoutFallback = raw.optPositiveLong("timeout_ms")
        val connectTimeout = raw.optPositiveLong("connect_timeout") ?: timeoutFallback ?: 10_000L
        val notifyTimeout = raw.optPositiveLong("result_waiting_timed_out") ?: timeoutFallback ?: 30_000L
        val overallTimeout = raw.optPositiveLong("overall_testing_timed_out") ?: timeoutFallback ?: 60_000L
        val scanActive = raw.optPositiveLong("scan_active")
            ?: raw.optPositiveLong("scan_duration")
            ?: 50L
        val uuidType = raw.optInt("uuid_type", 128)

        val preferredService = raw.optString("service2_uuid").trim().ifBlank {
            raw.optString("service_uuid").trim()
        }
        val preferredNotify = if (raw.optString("service2_uuid").trim().isNotBlank()) {
            raw.optString("notify2_uuid").trim().ifBlank {
                raw.optString("notify_uuid").trim()
            }
        } else {
            raw.optString("notify_uuid").trim()
        }
        val preferredWrite = if (raw.optString("service2_uuid").trim().isNotBlank()) {
            raw.optString("write2_uuid").trim().ifBlank {
                raw.optString("write_uuid").trim()
            }
        } else {
            raw.optString("write_uuid").trim()
        }

        return BleTestPlan(
            rssiMin = raw.optInt("rssi_min", -70),
            rssiMax = if (raw.has("rssi_max") && !raw.isNull("rssi_max")) raw.optInt("rssi_max") else null,
            scanIdleMs = raw.optPositiveLong("scan_idle") ?: 100L,
            scanActiveMs = scanActive,
            maxConcurrent = max(1, raw.optInt("max_concurrent", 1)),
            connectTimeoutMs = connectTimeout,
            notifyTimeoutMs = notifyTimeout,
            overallTimeoutMs = overallTimeout,
            serviceUuid = normalizeUuid(preferredService, uuidType),
            notifyCharacteristicUuid = normalizeUuid(preferredNotify, uuidType),
            writeCharacteristicUuid = normalizeUuid(preferredWrite, uuidType),
            writePayloadHex = raw.optString("test_command").normalizeHex(),
            expectedNotifyValueHex = raw.optString("expected_notify_value")
                .takeIf { it.isNotBlank() }
                ?.normalizeHex(),
            retryMax = max(0, raw.optInt("retry_max", 0)),
            retryIntervalMs = raw.optPositiveLong("retry_interval")
                ?: raw.optPositiveLong("retry_interval_ms")
                ?: 0L
        )
    }

    fun fromJson(rawJson: String): BleTestPlan {
        return normalize(JSONObject(rawJson))
    }

    private fun normalizeUuid(rawUuid: String, uuidType: Int): String {
        val source = rawUuid.trim()
        require(source.isNotBlank()) { "UUID is blank" }

        return when (uuidType) {
            16 -> {
                val hex = source.replace("-", "").uppercase()
                require(hex.length == 4) { "Invalid 16-bit UUID: $source" }
                "0000$hex-0000-1000-8000-00805F9B34FB"
            }
            32 -> {
                val hex = source.replace("-", "").uppercase()
                require(hex.length == 8) { "Invalid 32-bit UUID: $source" }
                "$hex-0000-1000-8000-00805F9B34FB"
            }
            else -> UUID.fromString(source).toString().uppercase()
        }
    }

    private fun JSONObject.optPositiveLong(key: String): Long? {
        if (!has(key) || isNull(key)) {
            return null
        }
        val value = optLong(key, Long.MIN_VALUE)
        return value.takeIf { it > 0L }
    }

    private fun String.normalizeHex(): String {
        return replace("\\s".toRegex(), "").uppercase()
    }
}
