package com.faray.leproducttest.data.batch

import com.faray.leproducttest.BuildConfig
import com.faray.leproducttest.common.AppError
import com.faray.leproducttest.common.AppErrorCode
import com.faray.leproducttest.common.AppException
import com.faray.leproducttest.model.BatchProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.math.max

object BatchApiClient {

    suspend fun fetchSummary(accessToken: String, batchId: String): Result<BatchProfile> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${BuildConfig.TEST_SERVER_BASE_URL}/api/production/summary?batch_id=${encode(batchId)}"
            val connection = openAuthorizedConnection(url, accessToken)

            try {
                val responseCode = connection.responseCode
                val responseText = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                    ?.readUtf8()
                    .orEmpty()

                if (responseCode !in 200..299) {
                    throw parseHttpError(responseText, responseCode)
                }

                parseBatchProfile(responseText)
            } catch (timeout: SocketTimeoutException) {
                throw AppException(
                    appError = AppError(
                        code = AppErrorCode.NETWORK_TIMEOUT,
                        message = "Timed out while loading batch summary"
                    ),
                    cause = timeout
                )
            } catch (jsonError: JSONException) {
                throw AppException(
                    appError = AppError(
                        code = AppErrorCode.RESPONSE_INVALID,
                        message = "Batch summary response is invalid"
                    ),
                    cause = jsonError
                )
            } catch (ioError: java.io.IOException) {
                throw AppException(
                    appError = AppError(
                        code = AppErrorCode.NETWORK_UNAVAILABLE,
                        message = "Failed to load batch summary"
                    ),
                    cause = ioError
                )
            } finally {
                connection.disconnect()
            }
        }
    }

    suspend fun downloadMacList(
        accessToken: String,
        batchId: String,
        destinationFile: File
    ): Result<MacListDownloadResult> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${BuildConfig.TEST_SERVER_BASE_URL}/api/production/mac-list/download?batch_id=${encode(batchId)}"
            val connection = openAuthorizedConnection(url, accessToken)

            try {
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    val errorText = connection.errorStream?.readUtf8().orEmpty()
                    throw parseHttpError(errorText, responseCode)
                }

                destinationFile.parentFile?.mkdirs()
                val lineCount = connection.inputStream.use { input ->
                    writeMacListToFile(input, destinationFile)
                }

                MacListDownloadResult(
                    file = destinationFile,
                    lineCount = lineCount,
                    eTag = connection.getHeaderField("ETag"),
                    version = connection.getHeaderField("X-Mac-List-Version"),
                    serverCount = connection.getHeaderField("X-Mac-List-Count")?.toIntOrNull()
                )
            } catch (timeout: SocketTimeoutException) {
                throw AppException(
                    appError = AppError(
                        code = AppErrorCode.NETWORK_TIMEOUT,
                        message = "Timed out while downloading MAC list"
                    ),
                    cause = timeout
                )
            } catch (ioError: java.io.IOException) {
                throw AppException(
                    appError = AppError(
                        code = AppErrorCode.NETWORK_UNAVAILABLE,
                        message = "Failed to download MAC list"
                    ),
                    cause = ioError
                )
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun openAuthorizedConnection(url: String, accessToken: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 20_000
            doInput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $accessToken")
        }
    }

    private fun parseBatchProfile(responseText: String): BatchProfile {
        val root = JSONObject(responseText)
        val data = root.optJSONObject("data")
            ?: throw JSONException("Missing data object")
        val bleConfig = data.optJSONObject("ble_config")
            ?: throw JSONException("Missing ble_config")

        return BatchProfile(
            batchId = data.optString("batch_id").ifBlank { throw JSONException("Missing batch_id") },
            expectedCount = data.optInt("expected_count", 0),
            expireTime = data.optString("expire_time"),
            expectedFirmware = data.optString("expected_firmware"),
            deviceType = data.optString("device_type"),
            bleNamePrefix = data.optString("ble_name_prefix"),
            bleNameRule = "prefix + 12HEX",
            bleConfig = BleConfigNormalizer.normalize(bleConfig),
            rawBleConfigJson = bleConfig.toString(),
            macListCount = data.optInt("mac_list_count", 0),
            macListHash = data.optString("mac_list_hash"),
            macListVersion = data.optString("mac_list_version"),
            macListUrl = data.optString("mac_list_url")
        )
    }

    private fun writeMacListToFile(input: InputStream, destinationFile: File): Int {
        BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { reader ->
            OutputStreamWriter(FileOutputStream(destinationFile), StandardCharsets.UTF_8).use { writer ->
                var lineCount = 0
                var line = reader.readLine()
                while (line != null) {
                    writer.write(line)
                    writer.write("\n")
                    if (line.isNotBlank()) {
                        lineCount += 1
                    }
                    line = reader.readLine()
                }
                writer.flush()
                return lineCount
            }
        }
    }

    private fun parseHttpError(responseText: String, responseCode: Int): AppException {
        val fallback = when (responseCode) {
            400 -> AppError(AppErrorCode.REQUEST_INVALID, "Request failed. Please try again.")
            401 -> AppError(AppErrorCode.AUTH_REQUIRED, "Login expired. Please sign in again.")
            404 -> AppError(AppErrorCode.BATCH_NOT_FOUND, "Batch files were not found on the server.")
            else -> AppError(AppErrorCode.INTERNAL, "Request failed. Please try again.")
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

    private fun encode(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    }
}

data class MacListDownloadResult(
    val file: File,
    val lineCount: Int,
    val eTag: String?,
    val version: String?,
    val serverCount: Int?
)
