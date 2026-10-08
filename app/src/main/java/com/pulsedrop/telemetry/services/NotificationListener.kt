package com.pulsedrop.telemetry.services

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.pulsedrop.telemetry.data.NotificationEntry
import com.pulsedrop.telemetry.data.UploadEnvelope
import com.pulsedrop.telemetry.utils.PrefsManager

class NotificationListener : NotificationListenerService() {

    companion object {
        var instance: NotificationListener? = null
        var onNotification: ((UploadEnvelope) -> Unit)? = null
    }

    private val prefs by lazy { PrefsManager(this) }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        if (!prefs.featureToggles.notifications) return

        val extras = sbn.notification.extras
        val title = extras.getString("android.title")
        val text = extras.getCharSequence("android.text")?.toString()

        val entry = NotificationEntry(
            id = "${sbn.key}_${sbn.postTime}",
            packageName = sbn.packageName,
            appName = getAppName(sbn.packageName),
            title = title,
            text = text,
            timestamp = sbn.postTime
        )

        onNotification?.invoke(
            UploadEnvelope("notification", prefs.deviceId, System.currentTimeMillis(), entry)
        )
    }

    private fun getAppName(packageName: String): String {
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Exception) {
            packageName
        }
    }
}
