package com.faray.leproducttest.ui.production

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.faray.leproducttest.R
import com.faray.leproducttest.databinding.ItemDeviceBinding
import com.faray.leproducttest.model.DeviceUiStatus
import com.faray.leproducttest.model.DeviceUiWarning
import com.faray.leproducttest.model.RuntimeDeviceItem
import com.faray.leproducttest.ui.deviceStatusLabel
import com.faray.leproducttest.ui.formatMac

class DeviceAdapter : ListAdapter<RuntimeDeviceItem, DeviceAdapter.DeviceViewHolder>(DiffCallback) {

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun getItemId(position: Int): Long = getItem(position).sequenceNo

    inner class DeviceViewHolder(
        private val binding: ItemDeviceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: RuntimeDeviceItem) {
            val style = styleFor(item.uiStatus)
            binding.textDeviceMac.text = formatMac(item.parsedMac)
            binding.textDeviceStatus.text = deviceStatusLabel(binding.root.context, item.uiStatus)
            binding.textDeviceMeta.text = "RSSI ${item.rssi} dBm  |  ${item.advName}"
            binding.textDeviceWarning.isVisible = item.warning == DeviceUiWarning.DUPLICATE_MAC_IN_PROGRESS

            binding.cardDevice.setCardBackgroundColor(ContextCompat.getColor(binding.root.context, style.background))
            binding.cardDevice.strokeColor = ContextCompat.getColor(
                binding.root.context,
                if (item.warning == DeviceUiWarning.DUPLICATE_MAC_IN_PROGRESS) {
                    R.color.state_warning_fg
                } else {
                    R.color.divider_subtle
                }
            )
            val foreground = ContextCompat.getColor(binding.root.context, style.foreground)
            binding.textDeviceMac.setTextColor(foreground)
            binding.textDeviceStatus.setTextColor(foreground)
        }

        private fun styleFor(status: DeviceUiStatus): DeviceStyle = when (status) {
            DeviceUiStatus.DISCOVERED -> DeviceStyle(R.color.state_default_bg, R.color.state_default_fg)
            DeviceUiStatus.INVALID_DEVICE -> DeviceStyle(R.color.state_invalid_bg, R.color.state_invalid_fg)
            DeviceUiStatus.ALREADY_TESTED -> DeviceStyle(R.color.state_already_bg, R.color.state_already_fg)
            DeviceUiStatus.QUEUED,
            DeviceUiStatus.CONNECTING,
            DeviceUiStatus.SUBSCRIBING,
            DeviceUiStatus.SENDING,
            DeviceUiStatus.WAITING_NOTIFY,
            DeviceUiStatus.DISCONNECTING -> DeviceStyle(R.color.state_running_bg, R.color.state_running_fg)
            DeviceUiStatus.PASS -> DeviceStyle(R.color.state_success_bg, R.color.state_success_fg)
            DeviceUiStatus.FAIL -> DeviceStyle(R.color.state_fail_bg, R.color.state_fail_fg)
        }
    }

    private data class DeviceStyle(
        val background: Int,
        val foreground: Int
    )

    private object DiffCallback : DiffUtil.ItemCallback<RuntimeDeviceItem>() {
        override fun areItemsTheSame(oldItem: RuntimeDeviceItem, newItem: RuntimeDeviceItem): Boolean {
            return oldItem.sequenceNo == newItem.sequenceNo
        }

        override fun areContentsTheSame(oldItem: RuntimeDeviceItem, newItem: RuntimeDeviceItem): Boolean {
            return oldItem == newItem
        }
    }
}
