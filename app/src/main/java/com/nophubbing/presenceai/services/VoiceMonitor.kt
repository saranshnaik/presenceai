package com.nophubbing.presenceai.services

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

class VoiceMonitor(private val context: Context) {

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun detectVoice(): Int {

        // AudioRecord-based ~5s sample, stored in a temporary PCM file.
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
        val tempFile = File(context.cacheDir, "presenceai_voice_temp.pcm")

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

            FileOutputStream(tempFile, false).use { out ->

                audioRecord.startRecording()

                val targetDurationMs = 5000L
                val startTime = System.currentTimeMillis()

                var sum = 0.0
                var totalSamples = 0

                while (System.currentTimeMillis() - startTime < targetDurationMs) {

                    val read = audioRecord.read(buffer, 0, buffer.size)

                    if (read <= 0) continue

                    // Write raw PCM16LE samples to temp file.
                    val byteBuffer =
                        ByteBuffer.allocate(read * 2)
                            .order(ByteOrder.LITTLE_ENDIAN)

                    for (i in 0 until read) {
                        byteBuffer.putShort(buffer[i])
                    }

                    out.write(byteBuffer.array())

                    // Accumulate energy for RMS.
                    for (i in 0 until read) {
                        val v = buffer[i].toDouble()
                        sum += v * v
                    }
                    totalSamples += read
                }

                audioRecord.stop()

                if (totalSamples <= 0) {
                    Log.d("PresenceAI", "VOICE_DETECTION_UNAVAILABLE no samples read")
                    return -1
                }

                val rms = sqrt(sum / totalSamples)

                // Threshold tuned empirically; just a coarse presence flag.
                // Earlier logs showed background RMS around ~700 and speech around ~1400,
                // so we choose a midpoint to detect typical speech.
                return if (rms > 1200) {
                    Log.d("PresenceAI", "DETECTED_VOICE rms=$rms samples=$totalSamples tempFile=${tempFile.absolutePath}")
                    1
                } else {
                    Log.d("PresenceAI", "NO_VOICE_DETECTED rms=$rms samples=$totalSamples tempFile=${tempFile.absolutePath}")
                    0
                }
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