package com.faray.leproducttest.common

import com.faray.leproducttest.model.UploadFailRecord
import com.faray.leproducttest.model.UploadInvalidRecord
import com.faray.leproducttest.model.UploadProductionResultDigestSource
import com.faray.leproducttest.model.UploadStatistics
import com.faray.leproducttest.model.UploadSuccessRecord
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object ReportDigestBuilder {

    fun buildReportDigest(source: UploadProductionResultDigestSource): String {
        val canonicalJson = buildCanonicalJson(source)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonicalJson.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    fun buildReportId(batchId: String, reportDigest: String): String {
        val safeBatchId = batchId.trim()
        return "${safeBatchId}_${reportDigest.take(16)}"
    }

    fun buildCanonicalJson(source: UploadProductionResultDigestSource): String {
        return buildString {
            append('{')
            appendJsonField("batch_id", source.batchId)
            append(',')
            appendJsonField("factory_id", source.factoryId)
            append(',')
            appendJsonField("app_version", source.appVersion)
            append(',')
            appendJsonField("test_start_time", source.testStartTime)
            append(',')
            appendJsonField("test_end_time", source.testEndTime)
            append(',')
            append("\"statistics\":")
            append(buildStatisticsJson(source.statistics))
            append(',')
            append("\"success_records\":")
            append(buildSuccessRecordsJson(source.successRecords))
            append(',')
            append("\"fail_records\":")
            append(buildFailRecordsJson(source.failRecords))
            append(',')
            append("\"invalid\":")
            append(buildInvalidRecordsJson(source.invalid))
            append('}')
        }
    }

    private fun buildStatisticsJson(statistics: UploadStatistics): String {
        return buildString {
            append('{')
            appendJsonField("expected_count", statistics.expectedCount)
            append(',')
            appendJsonField("actual_count", statistics.actualCount)
            append(',')
            appendJsonField("success_count", statistics.successCount)
            append(',')
            appendJsonField("fail_count", statistics.failCount)
            append(',')
            appendJsonField("success_rate", formatDecimal(statistics.successRate))
            append('}')
        }
    }

    private fun buildSuccessRecordsJson(records: List<UploadSuccessRecord>): String {
        return buildString {
            append('[')
            records.forEachIndexed { index, record ->
                if (index > 0) append(',')
                append('{')
                appendJsonField("mac", record.mac)
                append(',')
                appendJsonField("time", record.time)
                append('}')
            }
            append(']')
        }
    }

    private fun buildFailRecordsJson(records: List<UploadFailRecord>): String {
        return buildString {
            append('[')
            records.forEachIndexed { index, record ->
                if (index > 0) append(',')
                append('{')
                appendJsonField("mac", record.mac)
                append(',')
                appendJsonField("result", record.result)
                append(',')
                appendJsonField("reason", record.reason)
                append(',')
                appendJsonField("time", record.time)
                append('}')
            }
            append(']')
        }
    }

    private fun buildInvalidRecordsJson(records: List<UploadInvalidRecord>): String {
        return buildString {
            append('[')
            records.forEachIndexed { index, record ->
                if (index > 0) append(',')
                append('{')
                appendJsonField("mac", record.mac)
                append(',')
                appendJsonField("time", record.time)
                append('}')
            }
            append(']')
        }
    }

    private fun StringBuilder.appendJsonField(name: String, value: String) {
        append('"')
        append(name)
        append("\":")
        append('"')
        append(escapeJson(value))
        append('"')
    }

    private fun StringBuilder.appendJsonField(name: String, value: Int) {
        append('"')
        append(name)
        append("\":")
        append(value)
    }

    private fun StringBuilder.appendJsonField(name: String, value: Double) {
        append('"')
        append(name)
        append("\":")
        append(formatDecimal(value))
    }

    private fun formatDecimal(value: Double): String {
        return if (value.isFinite()) {
            java.math.BigDecimal.valueOf(value)
                .stripTrailingZeros()
                .toPlainString()
        } else {
            "0"
        }
    }

    private fun escapeJson(value: String): String {
        return buildString {
            value.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> {
                        if (ch.code < 0x20) {
                            append("\\u%04x".format(ch.code))
                        } else {
                            append(ch)
                        }
                    }
                }
            }
        }
    }
}
