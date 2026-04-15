package com.faray.leproducttest.data.runtime

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.faray.leproducttest.domain.service.RuntimeDeviceStore
import com.faray.leproducttest.model.RuntimeDeviceItem
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RuntimeDeviceStoreImpl : RuntimeDeviceStore {

    private val mutex = Mutex()
    private val items = linkedMapOf<Long, RuntimeDeviceItem>()
    private val visibleDevices = MutableLiveData<List<RuntimeDeviceItem>>(emptyList())

    override suspend fun save(item: RuntimeDeviceItem): RuntimeDeviceItem {
        mutex.withLock {
            items[item.parsedMac] = item
        }
        publish()
        return item
    }

    override suspend fun find(parsedMac: Long): RuntimeDeviceItem? {
        return mutex.withLock { items[parsedMac] }
    }

    override suspend fun remove(parsedMac: Long): RuntimeDeviceItem? {
        val removed = mutex.withLock {
            items.remove(parsedMac)
        }
        publish()
        return removed
    }

    override suspend fun clear() {
        mutex.withLock {
            items.clear()
        }
        publish()
    }

    override suspend fun snapshot(): List<RuntimeDeviceItem> {
        return mutex.withLock { items.values.sortedBy { it.sequenceNo } }
    }

    override fun observeVisibleDevices(): LiveData<List<RuntimeDeviceItem>> = visibleDevices

    private suspend fun publish() {
        visibleDevices.postValue(snapshot())
    }
}
