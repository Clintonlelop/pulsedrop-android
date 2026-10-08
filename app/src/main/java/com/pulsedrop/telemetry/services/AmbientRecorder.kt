package com.pulsedrop.telemetry.services

import android.content.Context
import android.media.MediaRecorder
import android.os.Environment
import java.io.File

class AmbientRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var isRecording = false

    fun getRecordingDir(): File {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "PulseDrop/AmbientRecordings"
        )
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun startRecording(): String? {
        if (isRecording) return currentFile?.absolutePath

        val fileName = "ambient_${System.currentTimeMillis()}.m4a"
        currentFile = File(getRecordingDir(), fileName)

        return try {
            recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(currentFile!!.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            currentFile!!.absolutePath
        } catch (e: Exception) {
            recorder = null
            null
        }
    }

    fun stopRecording(): File? {
        if (!isRecording) return null
        return try {
            recorder?.apply { stop(); release() }
            recorder = null
            isRecording = false
            currentFile
        } catch (e: Exception) {
            recorder = null
            isRecording = false
            null
        }
    }

    fun isRecording() = isRecording
}
