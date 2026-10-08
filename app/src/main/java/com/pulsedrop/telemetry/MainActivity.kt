package com.pulsedrop.telemetry

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.pulsedrop.telemetry.utils.PrefsManager

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager

    private val runtimePermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_SMS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.POST_NOTIFICATIONS
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = PrefsManager(this)
        prefs.ensureDeviceId()

        setupUI()
        showDisclaimer()
    }

    private fun setupUI() {
        val serverInput = findViewById<EditText>(R.id.serverInput)
        val tokenInput = findViewById<EditText>(R.id.tokenInput)
        val startBtn = findViewById<Button>(R.id.startBtn)
        val stopBtn = findViewById<Button>(R.id.stopBtn)
        val statusText = findViewById<TextView>(R.id.statusText)
        val deviceIdText = findViewById<TextView>(R.id.deviceIdText)
        val permsBtn = findViewById<Button>(R.id.permsBtn)
        val notifAccessBtn = findViewById<Button>(R.id.notifAccessBtn)
        val accessibilityBtn = findViewById<Button>(R.id.accessibilityBtn)

        serverInput.setText(prefs.serverUrl)
        tokenInput.setText(prefs.authToken)
        deviceIdText.text = "Device ID: ${prefs.deviceId}"

        startBtn.setOnClickListener {
            prefs.serverUrl = serverInput.text.toString().trim()
            prefs.authToken = tokenInput.text.toString().trim()

            if (prefs.serverUrl.isEmpty() || prefs.authToken.isEmpty()) {
                Toast.makeText(this, "Enter server URL and auth token", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (checkRuntimePermissions()) {
                startTelemetryService()
            } else {
                ActivityCompat.requestPermissions(this, runtimePermissions, 101)
            }
        }

        stopBtn.setOnClickListener {
            val intent = Intent(this, TelemetryService::class.java).apply {
                action = TelemetryService.ACTION_STOP
            }
            startService(intent)
            statusText.text = "Status: Stopped"
        }

        permsBtn.setOnClickListener {
            ActivityCompat.requestPermissions(this, runtimePermissions, 101)
        }

        notifAccessBtn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        accessibilityBtn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        // Update status
        statusText.text = if (prefs.isServiceRunning) "Status: Running" else "Status: Stopped"
    }

    private fun startTelemetryService() {
        val intent = Intent(this, TelemetryService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        findViewById<TextView>(R.id.statusText).text = "Status: Running"
        Toast.makeText(this, "PulseDrop telemetry started", Toast.LENGTH_SHORT).show()
    }

    private fun checkRuntimePermissions(): Boolean {
        return runtimePermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                startTelemetryService()
            } else {
                Toast.makeText(this, "Some permissions denied. Grant them in Settings.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showDisclaimer() {
        val prefs = getSharedPreferences("pulsedrop_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("disclaimer_shown", false)) return

        AlertDialog.Builder(this)
            .setTitle("PulseDrop — Personal Use Disclaimer")
            .setMessage(
                "This software is for personal testing on devices you own only.\n\n" +
                "Monitoring devices you do not own without full consent is illegal. " +
                "All data collection requires explicit permission grants from the device owner.\n\n" +
                "By using this app, you confirm you are testing on your own hardware."
            )
            .setPositiveButton("I Understand") { _, _ ->
                prefs.edit().putBoolean("disclaimer_shown", true).apply()
            }
            .setCancelable(false)
            .show()
    }
}
