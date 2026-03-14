package com.nophubbing.presenceai.signals

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlin.math.sqrt

/**
 * Detects Voice Activity (VAD) by analyzing ambient audio energy (x6).
 */
class VadScanner(private val context: Context) {

    /**
     * Samples audio for a short period and returns 1.0f if voice/speech energy is detected,
     * otherwise 0.0f. Returns 0.0f immediately if no microphone permission.
     */
    fun getVadEnergy(): Float {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return 0.0f
        }

        val sampleRate = 8000
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            return 0.0f
        }

        var audioRecord: AudioRecord? = null
        return try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                return 0.0f
            }

            audioRecord.startRecording()

            val buffer = ShortArray(bufferSize)
            // Read ~0.5 seconds of audio to detect energy
            var totalRead = 0
            val targetRead = sampleRate / 2
            var totalEnergy = 0.0

            while (totalRead < targetRead) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read < 0) break

                for (i in 0 until read) {
                    totalEnergy += buffer[i] * buffer[i]
                }
                totalRead += read
            }

            if (totalRead > 0) {
                val rms = sqrt(totalEnergy / totalRead)
                // Threshold calibration for voice energy
                if (rms > 500.0) 1.0f else 0.0f
            } else {
                0.0f
            }
        } catch (e: SecurityException) {
            0.0f
        } catch (e: Exception) {
            0.0f
        } finally {
            try {
                audioRecord?.stop()
                audioRecord?.release()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
    }
}
