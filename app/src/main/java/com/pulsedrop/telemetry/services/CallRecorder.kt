package com.pulsedrop.telemetry.services

import android.content.Context
import android.media.MediaRecorder
import android.os.Environment
import java.io.File

class CallRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var isRecording = false

    fun getRecordingDir(): File {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "PulseDrop/CallRecordings"
        )
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun startRecording(phoneNumber: String): String? {
        if (isRecording) return currentFile?.absolutePath

        return try {
            val fileName = "call_${phoneNumber.filter { it.isDigit() }}_${System.currentTimeMillis()}.m4a"
            currentFile = File(getRecordingDir(), fileName)

            recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder?.apply {
                // VOICE_CALL captures both uplink and downlink where supported by device.
                // Some OEMs block this; falls back to MIC on unsupported devices.
                setAudioSource(MediaRecorder.AudioSource.VOICE_CALL)
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
            // Fallback to MIC source if VOICE_CALL is blocked
            try {
                recorder?.release()
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
            } catch (e2: Exception) {
                null
            }
        }
    }

    fun stopRecording(): File? {
        if (!isRecording) return null
        return try {
            recorder?.apply {
                stop()
                release()
            }
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

    fun listRecordings(): List<File> {
        return getRecordingDir().listFiles { f -> f.extension == "m4a" }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
}
