package com.faray.leproducttest.ui

import android.content.Context
import com.faray.leproducttest.R
import com.faray.leproducttest.model.DeviceUiStatus
import com.faray.leproducttest.model.SessionStatus
import java.util.Locale

fun formatMac(mac: Long): String = String.format(Locale.US, "%012X", mac)

fun formatRate(rate: Double): String = String.format(Locale.US, "%.2f%%", rate)

fun deviceStatusLabel(context: Context, status: DeviceUiStatus): String = when (status) {
    DeviceUiStatus.DISCOVERED -> context.getString(R.string.status_discovered)
    DeviceUiStatus.INVALID_DEVICE -> context.getString(R.string.status_invalid)
    DeviceUiStatus.ALREADY_TESTED -> context.getString(R.string.status_already_tested)
    DeviceUiStatus.QUEUED -> context.getString(R.string.status_queued)
    DeviceUiStatus.CONNECTING -> context.getString(R.string.status_connecting)
    DeviceUiStatus.SUBSCRIBING -> context.getString(R.string.status_subscribing)
    DeviceUiStatus.SENDING -> context.getString(R.string.status_sending)
    DeviceUiStatus.WAITING_NOTIFY -> context.getString(R.string.status_waiting_notify)
    DeviceUiStatus.DISCONNECTING -> context.getString(R.string.status_disconnecting)
    DeviceUiStatus.PASS -> context.getString(R.string.status_pass)
    DeviceUiStatus.FAIL -> context.getString(R.string.status_fail)
}

fun sessionStatusLabel(context: Context, status: SessionStatus?): String = when (status) {
    SessionStatus.READY -> context.getString(R.string.state_ready)
    SessionStatus.RUNNING -> context.getString(R.string.state_running)
    SessionStatus.STOPPING -> context.getString(R.string.state_stopping)
    SessionStatus.STOPPED -> context.getString(R.string.state_stopped)
    SessionStatus.UPLOADED -> context.getString(R.string.state_uploaded)
    null -> context.getString(R.string.state_idle)
}
