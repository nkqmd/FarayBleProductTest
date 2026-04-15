package com.faray.leproducttest.ble.scan

import com.faray.leproducttest.model.BleTestPlan
import com.faray.leproducttest.model.ScanPayload

interface BleScannerEngine {
    fun start(
        plan: BleTestPlan,
        onPayload: (ScanPayload) -> Unit,
        onError: (Int) -> Unit
    )

    fun stop()
}
