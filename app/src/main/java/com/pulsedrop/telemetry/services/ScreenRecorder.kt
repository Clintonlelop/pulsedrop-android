package com.pulsedrop.telemetry.services

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Environment
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.File

class ScreenRecorder(private val context: Context) {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var isRecording = false

    fun getRecordingDir(): File {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "PulseDrop/ScreenRecordings"
        )
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun startRecording(resultCode: Int, data: Intent): String? {
        if (isRecording) return currentFile?.absolutePath

        val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpm.getMediaProjection(resultCode, data)

        val metrics = DisplayMetrics()
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.defaultDisplay.getRealMetrics(metrics)

        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val fileName = "screen_${System.currentTimeMillis()}.mp4"
        currentFile = File(getRecordingDir(), fileName)

        return try {
            recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder?.apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(currentFile!!.absolutePath)
                setVideoSize(width, height)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoEncodingBitRate(8_000_000)
                setVideoFrameRate(60)
                prepare()
            }

            val surface = recorder!!.surface
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "PulseDropScreenRecord",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface, null, null
            )

            recorder?.start()
            isRecording = true
            currentFile!!.absolutePath
        } catch (e: Exception) {
            cleanup()
            null
        }
    }

    fun stopRecording(): File? {
        if (!isRecording) return null
        return try {
            recorder?.apply {
                stop()
                release()
            }
            virtualDisplay?.release()
            mediaProjection?.stop()
            recorder = null
            virtualDisplay = null
            mediaProjection = null
            isRecording = false
            currentFile
        } catch (e: Exception) {
            cleanup()
            null
        }
    }

    private fun cleanup() {
        try { recorder?.release() } catch (e: Exception) {}
        try { virtualDisplay?.release() } catch (e: Exception) {}
        try { mediaProjection?.stop() } catch (e: Exception) {}
        recorder = null
        virtualDisplay = null
        mediaProjection = null
        isRecording = false
    }

    fun isRecording() = isRecording

    fun listRecordings(): List<File> {
        return getRecordingDir().listFiles { f -> f.extension == "mp4" }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
}
