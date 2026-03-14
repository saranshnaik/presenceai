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
                        "timestamp,x1_unlock_freq,x2_micro_session_ratio,x3_notification_reflex," +
                                "x4_behavior_drift_z,x5_time_phase,x6_vad_energy,x7_ble_social\n"
                    )
                }

                writer.append(
                    "${signals.timestamp}," +
                            "${signals.x1_unlock_freq}," +
                            "${signals.x2_micro_session_ratio}," +
                            "${signals.x3_notification_reflex}," +
                            "${signals.x4_behavior_drift_z}," +
                            "${signals.x5_time_phase}," +
                            "${signals.x6_vad_energy}," +
                            "${signals.x7_ble_social}\n"
                )
            }

        } catch (e: Exception) {

            Log.e("PresenceAI", "CSV write failed", e)
        }
    }
}