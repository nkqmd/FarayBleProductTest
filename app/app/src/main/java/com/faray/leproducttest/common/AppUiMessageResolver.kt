package com.faray.leproducttest.common

import com.faray.leproducttest.R

class AppUiMessageResolver(
    private val getString: (Int) -> String
) {

    fun loginCredentialsRequired(): String = getString(R.string.error_login_credentials_required)

    fun factoryIdRequired(): String = getString(R.string.error_factory_id_required)

    fun batchIdRequired(): String = getString(R.string.error_batch_id_required)

    fun invalidBatchId(): String = getString(R.string.error_batch_id_invalid)

    fun loginError(throwable: Throwable?): String {
        return when (AppErrorClassifier.fromThrowable(throwable, AppErrorCode.AUTH_REJECTED, getString(R.string.error_login_failed_generic)).code) {
            AppErrorCode.AUTH_REJECTED -> getString(R.string.message_login_failed)
            AppErrorCode.NETWORK_TIMEOUT -> getString(R.string.error_login_timeout)
            AppErrorCode.NETWORK_UNAVAILABLE -> getString(R.string.error_server_unreachable)
            AppErrorCode.RESPONSE_INVALID -> getString(R.string.error_login_response_invalid)
            else -> getString(R.string.error_login_failed_generic)
        }
    }

    fun authRestoreError(throwable: Throwable?): String {
        return when (AppErrorClassifier.fromThrowable(throwable, AppErrorCode.AUTH_REQUIRED, getString(R.string.message_auth_expired)).code) {
            AppErrorCode.AUTH_REQUIRED,
            AppErrorCode.AUTH_REJECTED -> getString(R.string.message_auth_expired)
            AppErrorCode.NETWORK_TIMEOUT -> getString(R.string.error_auth_restore_timeout)
            AppErrorCode.NETWORK_UNAVAILABLE -> getString(R.string.error_auth_restore_unreachable)
            AppErrorCode.RESPONSE_INVALID -> getString(R.string.error_auth_restore_response_invalid)
            else -> getString(R.string.message_auth_expired)
        }
    }

    fun batchSummaryError(throwable: Throwable?): String {
        return when (AppErrorClassifier.fromThrowable(throwable, AppErrorCode.INTERNAL, getString(R.string.error_batch_summary_failed)).code) {
            AppErrorCode.AUTH_REQUIRED,
            AppErrorCode.AUTH_REJECTED -> getString(R.string.message_auth_expired)
            AppErrorCode.NETWORK_TIMEOUT -> getString(R.string.error_batch_summary_timeout)
            AppErrorCode.NETWORK_UNAVAILABLE -> getString(R.string.error_batch_summary_unreachable)
            AppErrorCode.RESPONSE_INVALID -> getString(R.string.error_batch_summary_response_invalid)
            AppErrorCode.BATCH_NOT_FOUND -> getString(R.string.error_batch_not_found)
            else -> getString(R.string.error_batch_summary_failed)
        }
    }

    fun macListDownloadError(throwable: Throwable?): String {
        return when (AppErrorClassifier.fromThrowable(throwable, AppErrorCode.INTERNAL, getString(R.string.error_mac_list_download_failed)).code) {
            AppErrorCode.AUTH_REQUIRED,
            AppErrorCode.AUTH_REJECTED -> getString(R.string.message_auth_expired)
            AppErrorCode.NETWORK_TIMEOUT -> getString(R.string.error_mac_list_download_timeout)
            AppErrorCode.NETWORK_UNAVAILABLE -> getString(R.string.error_mac_list_download_unreachable)
            AppErrorCode.RESPONSE_INVALID -> getString(R.string.error_mac_list_download_response_invalid)
            AppErrorCode.BATCH_NOT_FOUND -> getString(R.string.error_batch_not_found)
            else -> getString(R.string.error_mac_list_download_failed)
        }
    }
}
