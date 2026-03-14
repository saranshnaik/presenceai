package com.nophubbing.presenceai.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * VoiceMonitor — real-time Voice Activity Detection using AudioRecord.
 *
 * Why AudioRecord instead of MediaRecorder:
 *  - Non-blocking: reads PCM frames, no temp file, no prepare/start/stop overhead
 *  - Returns a float confidence score (0.0–1.0) by normalising RMS amplitude
 *    against a speech-typical dBFS scale, rather than a raw binary 0/1
 *  - Samples for ~600 ms (enough for a syllable), releases the mic immediately
 *  - Safe to call on a background IO thread; does NOT block the caller for > 1s
 *
 * Return contract (matches MonitoringService usage):
 *   > 0f  — voice confidence [0.01 .. 1.0]
 *   = 0f  — silence / ambient noise below threshold
 *  -1f   — RECORD_AUDIO permission not granted
 *  -2f   — AudioRecord initialisation failed (another app holds the mic, etc.)
 *
 * The caller (MonitoringService) maps negative values → 0f for the ML pipeline
 * so missing VAD is treated as "no voice" rather than crashing inference.
 */
class VoiceMonitor(private val context: Context) {

    companion object {
        private const val TAG = "PresenceAI"

        // Audio config — 16 kHz mono 16-bit PCM is standard for speech
        private const val SAMPLE_RATE    = 16_000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT   = AudioFormat.ENCODING_PCM_16BIT

        // How many milliseconds of audio to capture per detection pass
        private const val CAPTURE_MS     = 600

        // RMS thresholds (raw 16-bit PCM values, range 0–32768).
        // Measured empirically: quiet room ambient ≈ 150–350 RMS, normal speech
        // at 0.5 m ≈ 1200–5000 RMS, loud speech ≈ 5000–12000 RMS.
        // Setting SILENCE_RMS at 400 filters out quiet ambient room noise properly.
        // Setting SPEECH_RMS at 2500 means confident speech detection.
        private const val SILENCE_RMS = 400f   // below this = room noise / silence
        private const val SPEECH_RMS  = 2_500f  // above this = confident human speech

        // dBFS normalisation bounds
        private val DB_MIN = 20.0 * log10(SILENCE_RMS.toDouble())
        private val DB_MAX = 20.0 * log10(10_000.0)
    }

    /**
     * Capture ~600 ms of mic audio and return a speech-confidence float.
     * Must be called from a background thread (AudioRecord.read blocks briefly).
     */
    fun detectVoice(): Float {
        // 1. Permission check
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "VAD: RECORD_AUDIO permission not granted")
            return -1f
        }

        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBuf == AudioRecord.ERROR || minBuf == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "VAD: AudioRecord.getMinBufferSize failed")
            return -2f
        }

        val bufferSize = maxOf(minBuf * 2, SAMPLE_RATE * CAPTURE_MS / 1_000 * 2)

        // Try audio sources in priority order.
        // VOICE_COMMUNICATION applies AGC/noise suppression — better for detecting
        // speech in noisy rooms on most OEM devices (MIUI, ColorOS, OneUI).
        // VOICE_RECOGNITION is cleaner for silence detection but misses soft speech on some ROMs.
        // MIC is the raw fallback — always available.
        val sourcesToTry = listOf(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC
        )

        var record: AudioRecord? = null
        for (source in sourcesToTry) {
            try {
                val r = AudioRecord(source, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize)
                if (r.state == AudioRecord.STATE_INITIALIZED) {
                    record = r
                    Log.d(TAG, "VAD: using audio source=$source")
                    break
                } else {
                    r.release()
                }
            } catch (_: Exception) { /* try next source */ }
        }

        if (record == null) {
            Log.e(TAG, "VAD: all audio sources failed (mic busy?)")
            return -2f
        }

        return try {
            record.startRecording()

            val totalSamples = SAMPLE_RATE * CAPTURE_MS / 1_000
            val buf = ShortArray(totalSamples)
            var offset = 0
            var attempts = 0

            while (offset < totalSamples && attempts < 10) {
                val read = record.read(buf, offset, totalSamples - offset)
                if (read > 0) offset += read else attempts++
            }

            record.stop()

            val samplesRead = offset
            if (samplesRead == 0) return 0f

            var sumSq = 0.0
            for (i in 0 until samplesRead) sumSq += buf[i].toDouble() * buf[i]
            val rms = sqrt(sumSq / samplesRead).toFloat()

            val confidence = when {
                rms < SILENCE_RMS -> 0f
                rms > SPEECH_RMS  -> 1f
                else -> {
                    val db = 20.0 * log10(rms.toDouble())
                    ((db - DB_MIN) / (DB_MAX - DB_MIN)).toFloat().coerceIn(0f, 1f)
                }
            }

            Log.d(TAG, "VAD: rms=${"%.0f".format(rms)} conf=${"%.2f".format(confidence)} → " + when {
                confidence >= 0.6f -> "STRONG VOICE"
                confidence >= 0.25f -> "voice likely"
                confidence > 0f    -> "ambient/low"
                else               -> "silence"
            })

            confidence

        } catch (e: Exception) {
            Log.e(TAG, "VAD: capture error: ${e.message}")
            0f
        } finally {
            record.release()
        }
    }
}
