package com.faray.leproducttest.data.auth

import com.faray.leproducttest.BuildConfig
import com.faray.leproducttest.common.AppError
import com.faray.leproducttest.common.AppErrorCode
import com.faray.leproducttest.common.AppException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.StandardCharsets

object AuthApiClient : AuthRemoteDataSource {

    override suspend fun login(username: String, password: String): Result<AuthTokens> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL("${BuildConfig.TEST_SERVER_BASE_URL}/api/login").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 10_000
                doInput = true
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }

            try {
                val requestBody = JSONObject()
                    .put("username", username)
                    .put("password", password)
                    .toString()
                OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                    writer.write(requestBody)
                    writer.flush()
                }

                val responseCode = connection.responseCode
                val responseText = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                    ?.readUtf8()
                    .orEmpty()

                if (responseCode !in 200..299) {
                    throw parseLoginHttpError(responseText, responseCode)
                }

                parseTokens(responseText)
            } catch (timeout: SocketTimeoutException) {
                throw AppException(
                    appError = AppError(
                        code = AppErrorCode.NETWORK_TIMEOUT,
                        message = "连接服务器超时，请确认 ${BuildConfig.TEST_SERVER_BASE_URL} 可访问"
                    ),
                    cause = timeout
                )
            } catch (jsonError: JSONException) {
                throw AppException(
                    appError = AppError(
                        code = AppErrorCode.RESPONSE_INVALID,
                        message = "服务器返回格式异常，无法完成登录"
                    ),
                    cause = jsonError
                )
            } catch (connectionError: java.io.IOException) {
                throw AppException(
                    appError = AppError(
                        code = AppErrorCode.NETWORK_UNAVAILABLE,
                        message = "无法连接到服务器 ${BuildConfig.TEST_SERVER_BASE_URL}"
                    ),
                    cause = connectionError
                )
            } finally {
                connection.disconnect()
            }
        }
    }

    override suspend fun refresh(refreshToken: String): Result<AuthTokens> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL("${BuildConfig.TEST_SERVER_BASE_URL}/api/refresh").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 10_000
                doInput = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $refreshToken")
            }

            try {
                val responseCode = connection.responseCode
                val responseText = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                    ?.readUtf8()
                    .orEmpty()

                if (responseCode !in 200..299) {
                    throw parseRefreshHttpError(responseText, responseCode)
                }

                parseTokens(responseText)
            } catch (timeout: SocketTimeoutException) {
                throw AppException(
                    appError = AppError(
                        code = AppErrorCode.NETWORK_TIMEOUT,
                        message = "连接服务器超时，请稍后重试"
                    ),
                    cause = timeout
                )
            } catch (jsonError: JSONException) {
                throw AppException(
                    appError = AppError(
                        code = AppErrorCode.RESPONSE_INVALID,
                        message = "服务器返回格式异常，无法刷新登录状态"
                    ),
                    cause = jsonError
                )
            } catch (connectionError: java.io.IOException) {
                throw AppException(
                    appError = AppError(
                        code = AppErrorCode.NETWORK_UNAVAILABLE,
                        message = "无法连接到服务器 ${BuildConfig.TEST_SERVER_BASE_URL}"
                    ),
                    cause = connectionError
                )
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun parseTokens(responseText: String): AuthTokens {
        val json = JSONObject(responseText)
        return AuthTokens(
            accessToken = json.getString("access_token"),
            accessExpiresInSeconds = json.getLong("access_expires"),
            refreshToken = json.getString("refresh_token"),
            refreshExpiresInSeconds = json.getLong("refresh_expires"),
            issuedAtMillis = System.currentTimeMillis()
        )
    }

    private fun parseLoginHttpError(responseText: String, responseCode: Int): AppException {
        val fallback = when (responseCode) {
            400 -> AppError(AppErrorCode.REQUEST_INVALID, "登录请求无效")
            401 -> AppError(AppErrorCode.AUTH_REJECTED, "Invalid username or password")
            else -> AppError(AppErrorCode.INTERNAL, "登录失败，请稍后重试")
        }
        val message = try {
            JSONObject(responseText).optString("msg").ifBlank {
                JSONObject(responseText).optString("message")
            }.ifBlank {
                fallback.message
            }
        } catch (_: JSONException) {
            fallback.message
        }
        return AppException(fallback.copy(message = message))
    }

    private fun parseRefreshHttpError(responseText: String, responseCode: Int): AppException {
        val fallback = when (responseCode) {
            400 -> AppError(AppErrorCode.REQUEST_INVALID, "刷新登录状态的请求无效")
            401 -> AppError(AppErrorCode.AUTH_REQUIRED, "Login expired. Please sign in again.")
            else -> AppError(AppErrorCode.INTERNAL, "刷新登录状态失败，请稍后重试")
        }
        val message = try {
            JSONObject(responseText).optString("msg").ifBlank {
                JSONObject(responseText).optString("message")
            }.ifBlank {
                fallback.message
            }
        } catch (_: JSONException) {
            fallback.message
        }
        val lowered = message.lowercase()
        val code = when {
            lowered.contains("revoked") || lowered.contains("invalid token") -> AppErrorCode.AUTH_REJECTED
            lowered.contains("expired") || lowered.contains("missing token") -> AppErrorCode.AUTH_REQUIRED
            else -> fallback.code
        }
        return AppException(AppError(code = code, message = message))
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
}
