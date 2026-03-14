package com.nophubbing.presenceai.services

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class VoiceMonitor(private val context: Context) {

    private var recorder: MediaRecorder? = null

    fun detectVoice(): Int {
        val tempFile = File(context.cacheDir, "voice_sample.3gp")
        
        return try {
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(tempFile.absolutePath)

                prepare()
                start()
            }

            // Listen for 1.5 seconds
            Thread.sleep(1500)

            val amplitude = recorder?.maxAmplitude ?: 0
            stopRecording()

            if (tempFile.exists()) tempFile.delete()

            if (amplitude > 2000) {
                Log.d("PresenceAI", "Voice detected amplitude=$amplitude")
                1
            } else {
                Log.d("PresenceAI", "No voice detected amplitude=$amplitude")
                0
            }

        } catch (e: Exception) {
            Log.e("PresenceAI", "Voice detection failed: ${e.message}")
            stopRecording()
            if (tempFile.exists()) tempFile.delete()
            -1
        }
    }

    private fun stopRecording() {
        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e("PresenceAI", "Error stopping recorder: ${e.message}")
        }
        recorder = null
    }
}