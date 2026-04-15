package com.faray.leproducttest.common

import com.faray.leproducttest.R
import org.junit.Assert.assertEquals
import org.junit.Test

class AppUiMessageResolverTest {

    private val resolver = AppUiMessageResolver(
        getString = { resId ->
            when (resId) {
                R.string.error_login_credentials_required -> "请输入用户名和密码"
                R.string.error_factory_id_required -> "请输入工位号"
                R.string.error_batch_id_required -> "请输入批次号"
                R.string.error_batch_id_invalid -> "批次号包含非法字符"
                R.string.error_login_failed_generic -> "登录失败，请稍后重试"
                R.string.error_login_timeout -> "登录超时，请稍后重试"
                R.string.error_server_unreachable -> "无法连接服务器，请检查网络或服务地址"
                R.string.error_login_response_invalid -> "登录响应异常，请稍后重试"
                R.string.message_login_failed -> "登录失败，请检查账号或密码"
                R.string.message_auth_expired -> "登录已失效，请重新登录"
                R.string.error_auth_restore_timeout -> "登录恢复超时，请稍后重试"
                R.string.error_auth_restore_unreachable -> "无法连接服务器，暂时无法恢复登录状态"
                R.string.error_auth_restore_response_invalid -> "登录恢复响应异常，请重新登录"
                R.string.error_batch_summary_failed -> "获取批次摘要失败，请稍后重试"
                R.string.error_batch_summary_timeout -> "获取批次摘要超时，请稍后重试"
                R.string.error_batch_summary_unreachable -> "无法连接服务器，暂时无法获取批次摘要"
                R.string.error_batch_summary_response_invalid -> "批次摘要返回异常，请稍后重试"
                R.string.error_batch_not_found -> "服务器上未找到该批次文件"
                R.string.error_mac_list_download_failed -> "下载 MAC 表失败，请稍后重试"
                R.string.error_mac_list_download_timeout -> "下载 MAC 表超时，请稍后重试"
                R.string.error_mac_list_download_unreachable -> "无法连接服务器，暂时无法下载 MAC 表"
                R.string.error_mac_list_download_response_invalid -> "MAC 表返回异常，请稍后重试"
                else -> error("Unhandled string resource: $resId")
            }
        }
    )

    @Test
    fun resolveLoginErrorFromNetworkTimeout() {
        val message = resolver.loginError(
            AppException(
                AppError(
                    code = AppErrorCode.NETWORK_TIMEOUT,
                    message = "连接服务器超时，请稍后重试"
                )
            )
        )

        assertEquals("登录超时，请稍后重试", message)
    }

    @Test
    fun resolveBatchSummaryErrorFromBatchNotFound() {
        val message = resolver.batchSummaryError(
            AppException(
                AppError(
                    code = AppErrorCode.BATCH_NOT_FOUND,
                    message = "Batch files were not found on the server."
                )
            )
        )

        assertEquals("服务器上未找到该批次文件", message)
    }

    @Test
    fun resolveMacListErrorFromAuthFailure() {
        val message = resolver.macListDownloadError(
            AppException(
                AppError(
                    code = AppErrorCode.AUTH_REQUIRED,
                    message = "Login expired. Please sign in again."
                )
            )
        )

        assertEquals("登录已失效，请重新登录", message)
    }
}
