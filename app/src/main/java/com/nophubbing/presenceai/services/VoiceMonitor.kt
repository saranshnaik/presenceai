package com.nophubbing.presenceai.services

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class VoiceMonitor(private val context: Context) {

    private var recorder: MediaRecorder? = null

    fun detectVoice(): Float {
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

            // Listen for 1.2 seconds, sampling periodically
            var maxObserved = 0
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 1200) {
                val amp = recorder?.maxAmplitude ?: 0
                if (amp > maxObserved) maxObserved = amp
                Thread.sleep(200)
            }

            stopRecording()

            if (tempFile.exists()) tempFile.delete()

            // Normalize: 16384 (half scale) -> 100
            val normalized = (maxObserved.toFloat() / 163.84f).coerceIn(0f, 100f)
            Log.d("PresenceAI", "Voice maxObserved=$maxObserved normalized=$normalized")
            normalized

        } catch (e: Exception) {
            Log.e("PresenceAI", "Voice detection failed: ${e.message}")
            stopRecording()
            if (tempFile.exists()) tempFile.delete()
            0f
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