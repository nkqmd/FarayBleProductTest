package com.faray.leproducttest.domain.service

import androidx.lifecycle.LiveData
import com.faray.leproducttest.model.RuntimeDeviceItem

interface RuntimeDeviceStore {
    suspend fun save(item: RuntimeDeviceItem): RuntimeDeviceItem
    suspend fun find(parsedMac: Long): RuntimeDeviceItem?
    suspend fun remove(parsedMac: Long): RuntimeDeviceItem?
    suspend fun clear()
    suspend fun snapshot(): List<RuntimeDeviceItem>
    fun observeVisibleDevices(): LiveData<List<RuntimeDeviceItem>>
}
