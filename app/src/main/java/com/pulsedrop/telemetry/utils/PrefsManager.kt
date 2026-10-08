package com.pulsedrop.telemetry.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.pulsedrop.telemetry.data.FeatureToggles

class PrefsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("pulsedrop_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    var serverUrl: String
        get() = prefs.getString("server_url", "https://your-server.com") ?: "https://your-server.com"
        set(value) = prefs.edit().putString("server_url", value).apply()

    var deviceId: String
        get() = prefs.getString("device_id", "") ?: ""
        set(value) = prefs.edit().putString("device_id", value).apply()

    var authToken: String
        get() = prefs.getString("auth_token", "") ?: ""
        set(value) = prefs.edit().putString("auth_token", value).apply()

    var featureToggles: FeatureToggles
        get() {
            val json = prefs.getString("feature_toggles", null)
            return if (json != null) gson.fromJson(json, FeatureToggles::class.java)
            else FeatureToggles()
        }
        set(value) = prefs.edit().putString("feature_toggles", gson.toJson(value)).apply()

    var geofenceZones: List<GeofenceZone>
        get() {
            val json = prefs.getString("geofence_zones", "[]")
            val type = object : com.google.gson.reflect.TypeToken<List<GeofenceZone>>() {}.type
            return gson.fromJson(json, type) ?: emptyList()
        }
        set(value) = prefs.edit().putString("geofence_zones", gson.toJson(value)).apply()

    var lastSimOperator: String?
        get() = prefs.getString("last_sim_operator", null)
        set(value) = prefs.edit().putString("last_sim_operator", value).apply()

    var isServiceRunning: Boolean
        get() = prefs.getBoolean("service_running", false)
        set(value) = prefs.edit().putBoolean("service_running", value).apply()

    fun ensureDeviceId(): String {
        var id = deviceId
        if (id.isEmpty()) {
            id = "pd_" + java.util.UUID.randomUUID().toString().substring(0, 8)
            deviceId = id
        }
        return id
    }
}

data class GeofenceZone(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val radiusMeters: Float
)
