package com.faray.leproducttest.domain.usecase

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.faray.leproducttest.common.AppError
import com.faray.leproducttest.common.AppErrorCode
import com.faray.leproducttest.common.AppException
import com.faray.leproducttest.model.BatchProfile
import com.faray.leproducttest.ui.config.ConfigUiState

class ValidateProductionStartUseCase {

    operator fun invoke(
        context: Context,
        configState: ConfigUiState
    ): Result<ProductionStartValidation> = runCatching {
        val batch = configState.currentBatch
        if (!configState.canStartProduction || batch == null) {
            throw AppException(
                AppError(
                    code = AppErrorCode.REQUEST_INVALID,
                    message = "Load a batch on the config page first"
                )
            )
        }

        val prefix = batch.bleNamePrefix.trim()
        if (prefix.isBlank()) {
            throw AppException(
                AppError(
                    code = AppErrorCode.REQUEST_INVALID,
                    message = "BLE prefix from batch summary is invalid"
                )
            )
        }

        if (!hasRequiredPermissions(context)) {
            throw AppException(
                AppError(
                    code = AppErrorCode.BLE_PERMISSION_REQUIRED,
                    message = "Bluetooth permissions are required before scanning"
                )
            )
        }

        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: throw AppException(
                AppError(
                    code = AppErrorCode.BLE_UNAVAILABLE,
                    message = "Bluetooth is not available on this device"
                )
            )
        if (!adapter.isEnabled) {
            throw AppException(
                AppError(
                    code = AppErrorCode.BLE_STATE_INVALID,
                    message = "Turn on Bluetooth before starting production"
                )
            )
        }
        if (!isLocationEnabled(context)) {
            throw AppException(
                AppError(
                    code = AppErrorCode.BLE_STATE_INVALID,
                    message = "Turn on Location before starting production"
                )
            )
        }

        ProductionStartValidation(
            batch = batch,
            prefix = prefix,
            staleTimeoutMs = batch.bleConfig.scanIdleMs.coerceAtLeast(1L) * 3L
        )
    }

    private fun hasRequiredPermissions(context: Context): Boolean {
        return REQUIRED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun isLocationEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        return locationManager?.isLocationEnabled == true
    }

    data class ProductionStartValidation(
        val batch: BatchProfile,
        val prefix: String,
        val staleTimeoutMs: Long
    )

    companion object {
        val requiredPermissions: Array<String>
            get() = REQUIRED_PERMISSIONS.copyOf()

        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
}
