package com.faray.leproducttest.data.repository

import com.faray.leproducttest.BuildConfig
import com.faray.leproducttest.common.AppError
import com.faray.leproducttest.common.AppErrorCode
import com.faray.leproducttest.common.AppException
import com.faray.leproducttest.common.UploadJsonCodec
import com.faray.leproducttest.data.local.dao.UploadRecordDao
import com.faray.leproducttest.data.local.entity.UploadRecordEntity
import com.faray.leproducttest.domain.repository.ResultUploadRepository
import com.faray.leproducttest.domain.usecase.UploadBatchReportUseCase
import com.faray.leproducttest.model.UploadBatchResultRequest
import com.faray.leproducttest.model.UploadBatchResultResponse
import com.faray.leproducttest.model.UploadProductionResultRequest
import com.faray.leproducttest.model.UploadProductionResultResponse
import com.faray.leproducttest.model.UploadRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID

class ResultUploadRepositoryImpl(
    private val uploadRecordDao: UploadRecordDao
) : ResultUploadRepository {

    override suspend fun upload(
        accessToken: String,
        request: UploadProductionResultRequest
    ): Result<UploadProductionResultResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL("${BuildConfig.TEST_SERVER_BASE_URL}/api/production/result/upload").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 20_000
                doInput = true
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $accessToken")
            }

            try {
                val requestBody = UploadJsonCodec.encodeRequest(request)
                OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                    writer.write(requestBody)
                    writer.flush()
                }

                val responseCode = connection.responseCode
                val responseText = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                    ?.readUtf8()
                    .orEmpty()

                if (responseCode !in 200..299) {
                    throw parseHttpError(responseText, responseCode)
                }

                parseUploadResponse(request, responseText)
            } catch (timeout: SocketTimeoutException) {
                throw AppException(
                    appError = AppError(
                        code = AppErrorCode.NETWORK_TIMEOUT,
                        message = "Timed out while uploading production result"
                    ),
                    cause = timeout
                )
            } catch (jsonError: JSONException) {
                throw AppException(
                    appError = AppError(
                        code = AppErrorCode.RESPONSE_INVALID,
                        message = "Upload response is invalid"
                    ),
                    cause = jsonError
                )
            } catch (ioError: java.io.IOException) {
                throw AppException(
                    appError = AppError(
                        code = AppErrorCode.NETWORK_UNAVAILABLE,
                        message = "Failed to upload production result"
                    ),
                    cause = ioError
                )
            } finally {
                connection.disconnect()
            }
        }
    }

    override suspend fun uploadBatch(
        accessToken: String,
        request: UploadBatchResultRequest
    ): Result<UploadBatchResultResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val boundary = "----FarayBatchUpload${UUID.randomUUID()}"
            val connection = (
                URL("${BuildConfig.TEST_SERVER_BASE_URL}/api/production/batch-result/upload-file")
                    .openConnection() as HttpURLConnection
                ).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 30_000
                doInput = true
                doOutput = true
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $accessToken")
            }

            try {
                DataOutputStream(connection.outputStream).use { output ->
                    output.writeMultipartField(boundary, "batch_id", request.batchId)
                    output.writeMultipartField(boundary, "factory_id", request.factoryId)
                    output.writeMultipartField(boundary, "batch_report_id", request.batchReportId)
                    output.writeMultipartField(boundary, "batch_report_digest", request.batchReportDigest)
                    output.writeMultipartField(boundary, "app_version", request.appVersion)
                    output.writeMultipartField(boundary, "aggregate_start_time", request.aggregateStartTime)
                    output.writeMultipartField(boundary, "aggregate_end_time", request.aggregateEndTime)
                    output.writeMultipartFile(
                        boundary = boundary,
                        fieldName = "file",
                        fileName = request.fileName,
                        contentType = "application/json",
                        bytes = request.fileBytes
                    )
                    output.writeUtf8("--$boundary--\r\n")
                    output.flush()
                }

                val responseCode = connection.responseCode
                val responseText = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                    ?.readUtf8()
                    .orEmpty()

                if (responseCode !in 200..299) {
                    throw parseHttpError(responseText, responseCode)
                }

                parseBatchUploadResponse(request, responseText)
            } catch (timeout: SocketTimeoutException) {
                throw AppException(
                    appError = AppError(
                        code = AppErrorCode.NETWORK_TIMEOUT,
                        message = "Timed out while uploading batch result"
                    ),
                    cause = timeout
                )
            } catch (jsonError: JSONException) {
                throw AppException(
                    appError = AppError(
                        code = AppErrorCode.RESPONSE_INVALID,
                        message = "Batch upload response is invalid"
                    ),
                    cause = jsonError
                )
            } catch (ioError: java.io.IOException) {
                throw AppException(
                    appError = AppError(
                        code = AppErrorCode.NETWORK_UNAVAILABLE,
                        message = "Failed to upload batch result"
                    ),
                    cause = ioError
                )
            } finally {
                connection.disconnect()
            }
        }
    }

    override suspend fun saveUploadRecord(record: UploadRecord) {
        uploadRecordDao.upsert(
            UploadRecordEntity(
                reportId = record.reportId,
                sessionId = record.sessionId,
                batchId = record.batchId,
                reportDigest = record.reportDigest,
                uploadStatus = "SUCCESS",
                uploadedAt = record.uploadedAt,
                serverUploadId = record.uploadId,
                duplicate = record.duplicate,
                message = record.message
            )
        )
    }

    override suspend fun saveBatchUploadRecord(record: UploadRecord) {
        saveUploadRecord(record.copy(sessionId = UploadBatchReportUseCase.batchUploadScope(record.batchId)))
    }

    override suspend fun getLatestUploadRecord(sessionId: String): UploadRecord? {
        return uploadRecordDao.getLatestBySession(sessionId)?.toDomain()
    }

    override suspend fun getLatestBatchUploadRecord(batchId: String): UploadRecord? {
        return uploadRecordDao.getLatestByBatchAndSession(
            batchId = batchId,
            sessionId = UploadBatchReportUseCase.batchUploadScope(batchId)
        )?.toDomain()
    }

    private fun parseUploadResponse(
        request: UploadProductionResultRequest,
        responseText: String
    ): UploadProductionResultResponse {
        val root = JSONObject(responseText)
        val message = root.optString("message").ifBlank { root.optString("msg") }
        val data = root.optJSONObject("data")
        return UploadProductionResultResponse(
            uploadId = data?.optString("upload_id")?.ifBlank { null },
            uploadTime = data?.optString("upload_time")?.ifBlank { null },
            reportId = data?.optString("report_id")?.ifBlank { null } ?: request.reportId,
            reportDigest = data?.optString("report_digest")?.ifBlank { null } ?: request.reportDigest,
            duplicate = data?.optBoolean("duplicate", false) ?: false,
            message = message.ifBlank { "upload success" }
        )
    }

    private fun parseBatchUploadResponse(
        request: UploadBatchResultRequest,
        responseText: String
    ): UploadBatchResultResponse {
        val root = JSONObject(responseText)
        val message = root.optString("message").ifBlank { root.optString("msg") }
        val data = root.optJSONObject("data")
        val uploadedAt = data?.optString("uploaded_at")
            ?.ifBlank { data.optString("upload_time").ifBlank { null } }
        return UploadBatchResultResponse(
            uploadId = data?.optString("upload_id")?.ifBlank { null },
            uploadedAt = uploadedAt,
            batchReportId = data?.optString("batch_report_id")?.ifBlank { null } ?: request.batchReportId,
            batchReportDigest = data?.optString("batch_report_digest")?.ifBlank { null } ?: request.batchReportDigest,
            duplicate = data?.optBoolean("duplicate", false) ?: false,
            message = message.ifBlank { "upload success" }
        )
    }

    private fun parseHttpError(responseText: String, responseCode: Int): AppException {
        val fallback = when (responseCode) {
            400 -> AppError(AppErrorCode.REQUEST_INVALID, "Upload payload is invalid.")
            401 -> AppError(AppErrorCode.AUTH_REQUIRED, "Login expired. Please sign in again.")
            else -> AppError(AppErrorCode.INTERNAL, "Upload failed. Please try again.")
        }
        val message = try {
            val json = JSONObject(responseText)
            json.optString("message")
                .ifBlank { json.optString("msg") }
                .ifBlank { fallback.message }
        } catch (_: JSONException) {
            fallback.message
        }
        return AppException(fallback.copy(message = message))
    }

    private fun UploadRecordEntity.toDomain(): UploadRecord {
        return UploadRecord(
            sessionId = sessionId,
            batchId = batchId,
            reportId = reportId,
            reportDigest = reportDigest,
            uploadId = serverUploadId,
            duplicate = duplicate,
            uploadedAt = uploadedAt,
            message = message
        )
    }

    private fun InputStream.readUtf8(): String {
        return BufferedReader(InputStreamReader(this, StandardCharsets.UTF_8)).use { reader ->
            buildString {
                var line = reader.readLine()
                while (line != null) {
                    append(line)
                    line = reader.readLine()
                }
            }
        }
    }

    private fun DataOutputStream.writeMultipartField(
        boundary: String,
        name: String,
        value: String
    ) {
        writeUtf8("--$boundary\r\n")
        writeUtf8("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
        writeUtf8(value)
        writeUtf8("\r\n")
    }

    private fun DataOutputStream.writeMultipartFile(
        boundary: String,
        fieldName: String,
        fileName: String,
        contentType: String,
        bytes: ByteArray
    ) {
        writeUtf8("--$boundary\r\n")
        writeUtf8(
            "Content-Disposition: form-data; name=\"$fieldName\"; filename=\"$fileName\"\r\n"
        )
        writeUtf8("Content-Type: $contentType\r\n\r\n")
        write(bytes)
        writeUtf8("\r\n")
    }

    private fun DataOutputStream.writeUtf8(value: String) {
        write(value.toByteArray(StandardCharsets.UTF_8))
    }
}
