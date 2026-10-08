package com.pulsedrop.telemetry.collectors

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.telephony.TelephonyManager
import com.pulsedrop.telemetry.data.DeviceInfo
import com.pulsedrop.telemetry.data.SimChangeEvent
import com.pulsedrop.telemetry.data.UploadEnvelope
import com.pulsedrop.telemetry.utils.PrefsManager
import java.net.InetAddress
import java.net.NetworkInterface

class DeviceInfoCollector(
    private val context: Context,
    private val onData: (UploadEnvelope) -> Unit
) {
    private val prefs = PrefsManager(context)

    fun collect(): DeviceInfo {
        val battery = getBatteryLevel()
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val simState = when (telephony.simState) {
            TelephonyManager.SIM_STATE_READY -> "ready"
            TelephonyManager.SIM_STATE_ABSENT -> "absent"
            TelephonyManager.SIM_STATE_PIN_REQUIRED -> "pin_required"
            TelephonyManager.SIM_STATE_PUK_REQUIRED -> "puk_required"
            TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "network_locked"
            else -> "unknown"
        }

        val info = DeviceInfo(
            batteryLevel = battery.first,
            isCharging = battery.second,
            networkType = getNetworkType(),
            wifiSsid = getWifiSsid(),
            ipAddress = getIpAddress(),
            deviceModel = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            osVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            simStatus = simState,
            simOperator = telephony.simOperatorName,
            timestamp = System.currentTimeMillis()
        )

        onData(UploadEnvelope("device_info", prefs.deviceId, System.currentTimeMillis(), info))
        return info
    }

    fun checkSimChange() {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val current = telephony.simOperatorName
        val previous = prefs.lastSimOperator

        if (previous != null && previous != current) {
            val event = SimChangeEvent(
                previousSimOperator = previous,
                newSimOperator = current,
                timestamp = System.currentTimeMillis()
            )
            onData(UploadEnvelope("sim_change", prefs.deviceId, System.currentTimeMillis(), event))
        }
        prefs.lastSimOperator = current
    }

    private fun getBatteryLevel(): Pair<Int, Boolean> {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, filter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level != -1 && scale != -1) (level * 100 / scale) else 0
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        return Pair(pct, charging)
    }

    private fun getNetworkType(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return "none"
        val caps = cm.getNetworkCapabilities(network) ?: return "unknown"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "unknown"
        }
    }

    @SuppressLint("MissingPermission")
    private fun getWifiSsid(): String? {
        return try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wifi.connectionInfo
            if (info.ssid != null && info.ssid != "<unknown ssid>") info.ssid.replace("\"", "") else null
        } catch (e: Exception) {
            null
        }
    }

    private fun getIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is InetAddress && addr.hostAddress?.indexOf(':') == -1) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) { /* ignore */ }
        return "0.0.0.0"
    }
}
