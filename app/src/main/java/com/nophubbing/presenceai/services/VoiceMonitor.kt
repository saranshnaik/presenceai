package com.nophubbing.presenceai.services

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlin.math.sqrt

class VoiceMonitor {

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun detectVoice(): Int {

        // AudioRecord-based short sample, no data persisted.
        val sampleRate = 16_000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT

        val minBufferSize =
            AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        if (minBufferSize <= 0) {
            Log.d("PresenceAI", "VOICE_DETECTION_UNAVAILABLE invalid bufferSize=$minBufferSize")
            return -1
        }

        val buffer = ShortArray(minBufferSize)

        var audioRecord: AudioRecord? = null

        return try {

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                minBufferSize
            )

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                Log.d("PresenceAI", "VOICE_DETECTION_UNAVAILABLE AudioRecord not initialized")
                return -1
            }

            audioRecord.startRecording()

            val read = audioRecord.read(buffer, 0, buffer.size)

            audioRecord.stop()

            if (read <= 0) {
                Log.d("PresenceAI", "VOICE_DETECTION_UNAVAILABLE no samples read")
                return -1
            }

            // Compute simple RMS energy.
            var sum = 0.0
            for (i in 0 until read) {
                val v = buffer[i].toDouble()
                sum += v * v
            }
            val rms = sqrt(sum / read)

            // Threshold tuned empirically; just a coarse presence flag.
            return if (rms > 2000) {
                Log.d("PresenceAI", "DETECTED_VOICE rms=$rms samples=$read")
                1
            } else {
                Log.d("PresenceAI", "NO_VOICE_DETECTED rms=$rms samples=$read")
                0
            }

        } catch (e: Exception) {

            Log.d("PresenceAI", "VOICE_DETECTION_UNAVAILABLE ${e.message}")
            -1

        } finally {

            try {
                audioRecord?.release()
            } catch (_: Exception) {
            }
        }
    }
}