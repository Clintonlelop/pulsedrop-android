package com.pulsedrop.telemetry

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast

class MediaProjectionActivity : Activity() {

    companion object {
        private const val REQUEST_CODE = 2001
        private var pendingAction: String? = null
        private var pendingCommandId: String? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingAction = intent.getStringExtra("action")
        pendingCommandId = intent.getStringExtra("command_id")

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mpm.createScreenCaptureIntent(), REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            val serviceIntent = Intent(this, TelemetryService::class.java).apply {
                action = when (pendingAction) {
                    "screenshot" -> TelemetryService.ACTION_SCREENSHOT_RESULT
                    "screen_record" -> TelemetryService.ACTION_SCREEN_RECORD_RESULT
                    else -> null
                }
                putExtra("resultCode", resultCode)
                putExtra("data", data)
            }
            if (serviceIntent.action != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            }
        } else {
            Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
