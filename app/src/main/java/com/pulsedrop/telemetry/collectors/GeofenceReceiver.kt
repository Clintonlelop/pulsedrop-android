package com.pulsedrop.telemetry.collectors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.pulsedrop.telemetry.data.GeofenceEvent

class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return

        val transition = when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> "enter"
            Geofence.GEOFENCE_TRANSITION_EXIT -> "exit"
            else -> return
        }

        val triggering = event.triggeringLocation
        event.triggeringGeofences?.forEach { geofence ->
            val geoEvent = GeofenceEvent(
                geofenceId = geofence.requestId,
                transition = transition,
                lat = triggering?.latitude ?: 0.0,
                lon = triggering?.longitude ?: 0.0,
                timestamp = System.currentTimeMillis()
            )
            // Forward to service via intent
            val serviceIntent = Intent(context, com.pulsedrop.telemetry.TelemetryService::class.java).apply {
                action = "GEOFENCE_EVENT"
                putExtra("event", android.os.Bundle().apply {
                    putString("id", geoEvent.geofenceId)
                    putString("transition", geoEvent.transition)
                    putDouble("lat", geoEvent.lat)
                    putDouble("lon", geoEvent.lon)
                    putLong("timestamp", geoEvent.timestamp)
                })
            }
            context.startService(serviceIntent)
        }
    }
}
