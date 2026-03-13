package com.nophubbing.presenceai.services

import android.media.MediaRecorder
import android.util.Log
import java.io.IOException

class VoiceMonitor {

    private var recorder: MediaRecorder? = null

    fun detectVoice(): Int {

        return try {

            recorder = MediaRecorder().apply {

                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile("/dev/null")

                prepare()
                start()
            }

            Thread.sleep(1500)

            val amplitude = recorder?.maxAmplitude ?: 0

            stopRecording()

            if (amplitude > 2000) {
                Log.d("PresenceAI", "Voice detected amplitude=$amplitude")
                1
            } else {
                0
            }

        } catch (e: IOException) {

            Log.e("PresenceAI", "Voice detection failed", e)
            -1
        }
    }

    private fun stopRecording() {

        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {
        }

        recorder = null
    }
}