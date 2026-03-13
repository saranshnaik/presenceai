package com.nophubbing.presenceai.storage

import android.content.Context
import android.util.Log
import com.nophubbing.presenceai.analytics.BehaviorSignals
import java.io.File
import java.io.FileWriter

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

                writer.append(
                    "${signals.timestamp}," +
                            "${signals.unlocks}," +
                            "${signals.microSessions}," +
                            "${signals.notificationReflex}," +
                            "${signals.behaviorDrift}," +
                            "${signals.timePhase}," +
                            "${signals.voiceDetected}," +
                            "${signals.proximityDetected}\n"
                )
            }

        } catch (e: Exception) {

            Log.e("PresenceAI", "CSV write failed", e)
        }
    }
}