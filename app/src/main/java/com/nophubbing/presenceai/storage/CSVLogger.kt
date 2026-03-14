package com.nophubbing.presenceai.storage

import android.content.Context
import android.util.Log
import com.nophubbing.presenceai.analytics.BehaviorSignals
import java.io.File
import java.io.FileWriter
import java.util.Locale

class CSVLogger(private val context: Context) {

    private val fileName = "presenceai_dataset.csv"

    fun logSignals(signals: BehaviorSignals) {

        try {

            val file = File(context.filesDir, fileName)

            FileWriter(file, true).use { writer ->

                if (file.length() == 0L) {

                    writer.append(
                        "timestamp,unlocks,micro_sessions,notification_reflex," +
                                "behavior_drift,time_phase,voice,proximity\n"
                    )
                }

                val behaviorDriftFormatted =
                    String.format(Locale.US, "%.4f", signals.behaviorDrift)

                val row = buildString {
                    append(signals.timestamp)
                    append(',')
                    append(signals.unlocks)
                    append(',')
                    append(signals.microSessions)
                    append(',')
                    append(signals.notificationReflex)
                    append(',')
                    append(behaviorDriftFormatted)
                    append(',')
                    append(signals.timePhase)
                    append(',')
                    append(signals.voiceDetected)
                    append(',')
                    append(signals.proximityDetected)
                    append('\n')
                }

                writer.append(row)

                Log.d("PresenceAI", "CSV row written: $row")
            }

        } catch (e: Exception) {
            Log.e("PresenceAI", "CSV write failed", e)
        }
    }
}