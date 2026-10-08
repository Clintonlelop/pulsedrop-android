package com.pulsedrop.telemetry.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pulsedrop.telemetry.data.ServerCommand
import com.pulsedrop.telemetry.data.UploadEnvelope
import kotlinx.coroutines.*
import okhttp3.*
import okio.ByteString
import java.io.File
import java.util.concurrent.TimeUnit

class NetworkClient(
    private val serverUrl: String,
    private val deviceId: String,
    private val authToken: String
) {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var ws: WebSocket? = null
    var onCommand: ((ServerCommand) -> Unit)? = null
    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // --- HTTPS data upload ---

    fun uploadData(envelope: UploadEnvelope, callback: (Boolean) -> Unit = {}) {
        scope.launch {
            try {
                val body = RequestBody.create(
                    MediaType.parse("application/json"),
                    gson.toJson(envelope)
                )
                val request = Request.Builder()
                    .url("$serverUrl/api/upload")
                    .addHeader("Authorization", "Bearer $authToken")
                    .addHeader("X-Device-Id", deviceId)
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    callback(response.isSuccessful)
                }
            } catch (e: Exception) {
                callback(false)
            }
        }
    }

    fun uploadFile(file: File, type: String, callback: (Boolean, String?) -> Unit = { _, _ -> }) {
        scope.launch {
            try {
                val body = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("type", type)
                    .addFormDataPart("device_id", deviceId)
                    .addFormDataPart(
                        "file", file.name,
                        RequestBody.create(MediaType.parse("application/octet-stream"), file)
                    )
                    .build()

                val request = Request.Builder()
                    .url("$serverUrl/api/upload-file")
                    .addHeader("Authorization", "Bearer $authToken")
                    .addHeader("X-Device-Id", deviceId)
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    val bodyStr = response.body()?.string()
                    callback(response.isSuccessful, bodyStr)
                }
            } catch (e: Exception) {
                callback(false, null)
            }
        }
    }

    // --- WebSocket command channel ---

    fun connectWebSocket() {
        val wsUrl = serverUrl.replace("https://", "wss://").replace("http://", "ws://")
        val request = Request.Builder()
            .url("$wsUrl/ws?device_id=$deviceId&token=$authToken")
            .build()

        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("PulseDrop", "WebSocket connected")
                onConnected?.invoke()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val type = object : TypeToken<ServerCommand>() {}.type
                    val cmd = gson.fromJson<ServerCommand>(text, type)
                    onCommand?.invoke(cmd)
                } catch (e: Exception) {
                    Log.e("PulseDrop", "WS parse error: ${e.message}")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("PulseDrop", "WS failure: ${t.message}")
                onDisconnected?.invoke()
                // Reconnect after delay
                scope.launch {
                    delay(5000)
                    connectWebSocket()
                }
            }
        })
    }

    fun sendCommandResponse(response: Any) {
        ws?.send(gson.toJson(response))
    }

    fun sendHeartbeat() {
        ws?.send(gson.toJson(mapOf("type" to "heartbeat", "device_id" to deviceId, "timestamp" to System.currentTimeMillis())))
    }

    fun disconnect() {
        ws?.close(1000, "client disconnect")
        ws = null
    }
}
