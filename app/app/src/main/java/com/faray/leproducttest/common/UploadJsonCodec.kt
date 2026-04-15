package com.faray.leproducttest.common

import com.faray.leproducttest.model.UploadBatchResultFilePayload
import com.faray.leproducttest.model.UploadIncludedSession
import com.faray.leproducttest.model.UploadProductionResultRequest
import org.json.JSONArray
import org.json.JSONObject

object UploadJsonCodec {

    fun encodeRequest(request: UploadProductionResultRequest): String {
        return JSONObject()
            .put("report_id", request.reportId)
            .put("report_digest", request.reportDigest)
            .put("batch_id", request.batchId)
            .put("factory_id", request.factoryId)
            .put("app_version", request.appVersion)
            .put("test_start_time", request.testStartTime)
            .put("test_end_time", request.testEndTime)
            .put(
                "statistics",
                JSONObject()
                    .put("expected_count", request.statistics.expectedCount)
                    .put("actual_count", request.statistics.actualCount)
                    .put("success_count", request.statistics.successCount)
                    .put("fail_count", request.statistics.failCount)
                    .put("success_rate", request.statistics.successRate)
            )
            .put(
                "success_records",
                JSONArray().apply {
                    request.successRecords.forEach { record ->
                        put(
                            JSONObject()
                                .put("mac", record.mac)
                                .put("time", record.time)
                        )
                    }
                }
            )
            .put(
                "fail_records",
                JSONArray().apply {
                    request.failRecords.forEach { record ->
                        put(
                            JSONObject()
                                .put("mac", record.mac)
                                .put("result", record.result)
                                .put("reason", record.reason)
                                .put("time", record.time)
                        )
                    }
                }
            )
            .put(
                "invalid",
                JSONArray().apply {
                    request.invalid.forEach { record ->
                        put(
                            JSONObject()
                                .put("mac", record.mac)
                                .put("time", record.time)
                        )
                    }
                }
            )
            .toString()
    }

    fun encodeBatchFilePayload(payload: UploadBatchResultFilePayload): String {
        return JSONObject()
            .put("batch_id", payload.batchId)
            .put("factory_id", payload.factoryId)
            .put("app_version", payload.appVersion)
            .put("aggregate_start_time", payload.aggregateStartTime)
            .put("aggregate_end_time", payload.aggregateEndTime)
            .put(
                "statistics",
                JSONObject()
                    .put("expected_count", payload.statistics.expectedCount)
                    .put("actual_count", payload.statistics.actualCount)
                    .put("success_count", payload.statistics.successCount)
                    .put("fail_count", payload.statistics.failCount)
                    .put("invalid_count", payload.statistics.invalidCount)
                    .put("success_rate", payload.statistics.successRate)
            )
            .put(
                "included_sessions",
                JSONArray().apply {
                    payload.includedSessions.forEach { session ->
                        put(encodeIncludedSession(session))
                    }
                }
            )
            .put(
                "success_records",
                JSONArray().apply {
                    payload.successRecords.forEach { record ->
                        put(
                            JSONObject()
                                .put("session_id", record.sessionId)
                                .put("mac", record.mac)
                                .put("time", record.time)
                        )
                    }
                }
            )
            .put(
                "fail_records",
                JSONArray().apply {
                    payload.failRecords.forEach { record ->
                        put(
                            JSONObject()
                                .put("session_id", record.sessionId)
                                .put("mac", record.mac)
                                .put("result", record.result)
                                .put("reason", record.reason)
                                .put("time", record.time)
                        )
                    }
                }
            )
            .put(
                "invalid",
                JSONArray().apply {
                    payload.invalid.forEach { record ->
                        put(
                            JSONObject()
                                .put("session_id", record.sessionId)
                                .put("mac", record.mac)
                                .put("time", record.time)
                        )
                    }
                }
            )
            .toString()
    }

    private fun encodeIncludedSession(session: UploadIncludedSession): JSONObject {
        return JSONObject()
            .put("session_id", session.sessionId)
            .put("test_start_time", session.testStartTime)
            .put("test_end_time", session.testEndTime)
    }
}
