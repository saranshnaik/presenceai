package com.nophubbing.presenceai.services

import android.media.MediaRecorder
import android.util.Log

class VoiceMonitor {

    fun detectVoice(): Int {

        var recorder: MediaRecorder? = null

        return try {

            recorder = MediaRecorder()

            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            recorder.setOutputFile("/dev/null")

            recorder.prepare()
            recorder.start()

            Thread.sleep(1500)

            val amplitude = recorder.maxAmplitude

            try {
                recorder.stop()
            } catch (_: Exception) {}

            if (amplitude > 2000) {
                Log.d("PresenceAI", "Voice detected amplitude=$amplitude")
                1
            } else {
                0
            }

        } catch (e: Exception) {

            Log.d("PresenceAI", "Voice detection unavailable")

            -1

        } finally {

            try {
                recorder?.release()
            } catch (_: Exception) {}
        }
    }
}