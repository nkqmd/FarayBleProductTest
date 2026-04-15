package com.faray.leproducttest.ble.scan

import com.faray.leproducttest.model.BleTestPlan
import com.faray.leproducttest.model.ScanPayload
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat
import no.nordicsemi.android.support.v18.scanner.ScanCallback
import no.nordicsemi.android.support.v18.scanner.ScanResult
import no.nordicsemi.android.support.v18.scanner.ScanSettings

class NordicBleScannerEngine : BleScannerEngine {

    private val scanner = BluetoothLeScannerCompat.getScanner()
    private var callback: ScanCallback? = null

    override fun start(
        plan: BleTestPlan,
        onPayload: (ScanPayload) -> Unit,
        onError: (Int) -> Unit
    ) {
        if (callback != null) {
            return
        }
        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                onPayload(result.toPayload())
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { onPayload(it.toPayload()) }
            }

            override fun onScanFailed(errorCode: Int) {
                onError(errorCode)
            }
        }
        callback = scanCallback
        scanner.startScan(
            null,
            ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setUseHardwareFilteringIfSupported(false)
                .setUseHardwareBatchingIfSupported(false)
                .setUseHardwareCallbackTypesIfSupported(false)
                .build(),
            scanCallback
        )
    }

    override fun stop() {
        val activeCallback = callback ?: return
        runCatching { scanner.stopScan(activeCallback) }
        callback = null
    }

    private fun ScanResult.toPayload(): ScanPayload {
        return ScanPayload(
            deviceAddress = device.address.orEmpty(),
            advName = scanRecord?.deviceName ?: device.name.orEmpty(),
            rssi = rssi,
            seenAt = System.currentTimeMillis()
        )
    }
}
