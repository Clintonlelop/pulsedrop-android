package com.pulsedrop.telemetry.collectors

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.*
import com.pulsedrop.telemetry.data.GeofenceEvent
import com.pulsedrop.telemetry.data.LocationData
import com.pulsedrop.telemetry.data.UploadEnvelope
import com.pulsedrop.telemetry.utils.GeofenceZone
import com.pulsedrop.telemetry.utils.PrefsManager

class LocationCollector(
    private val context: Context,
    private val onData: (UploadEnvelope) -> Unit
) {
    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    private val geofencingClient = LocationServices.getGeofencingClient(context)
    private val prefs = PrefsManager(context)
    private var locationCallback: LocationCallback? = null
    private var geofencePendingIntent: android.app.PendingIntent? = null

    @SuppressLint("MissingPermission")
    fun startLocationUpdates(intervalMs: Long = 30000) {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { emitLocation(it) }
            }
        }
        fusedClient.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
    }

    fun stopLocationUpdates() {
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        locationCallback = null
    }

    @SuppressLint("MissingPermission")
    fun pullCurrentLocation(callback: (LocationData?) -> Unit) {
        fusedClient.lastLocation.addOnSuccessListener { loc ->
            callback(loc?.let { toData(it) })
        }.addOnFailureListener { callback(null) }
    }

    private fun emitLocation(loc: Location) {
        val data = toData(loc)
        onData(UploadEnvelope("location", prefs.deviceId, System.currentTimeMillis(), data))
    }

    private fun toData(loc: Location) = LocationData(
        lat = loc.latitude,
        lon = loc.longitude,
        accuracy = loc.accuracy,
        altitude = loc.altitude,
        speed = loc.speed,
        timestamp = loc.time,
        provider = loc.provider ?: "unknown"
    )

    // --- Geofencing ---

    @SuppressLint("MissingPermission")
    fun registerGeofences(zones: List<GeofenceZone>) {
        if (zones.isEmpty()) return
        val geofences = zones.map { zone ->
            Geofence.Builder()
                .setRequestId(zone.id)
                .setCircularRegion(zone.lat, zone.lon, zone.radiusMeters)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                .build()
        }

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(geofences)
            .build()

        val intent = android.content.Intent(context, GeofenceReceiver::class.java)
        geofencePendingIntent = android.app.PendingIntent.getBroadcast(
            context, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        geofencingClient.addGeofences(request, geofencePendingIntent!!)
            .addOnSuccessListener { /* registered */ }
            .addOnFailureListener { /* failed */ }
    }

    fun removeGeofences() {
        geofencePendingIntent?.let { geofencingClient.removeGeofences(it) }
        geofencePendingIntent = null
    }

    fun emitGeofenceEvent(event: GeofenceEvent) {
        onData(UploadEnvelope("geofence", prefs.deviceId, System.currentTimeMillis(), event))
    }
}
