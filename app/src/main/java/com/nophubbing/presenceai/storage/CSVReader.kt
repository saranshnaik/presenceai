package com.nophubbing.presenceai.storage

import android.content.Context
import com.nophubbing.presenceai.analytics.BehaviorSignals
import java.io.BufferedReader
import java.io.File

object CSVReader {

    fun readLatestSignals(context: Context): BehaviorSignals? {

        try {

            val file = File(context.filesDir, "presenceai_dataset.csv")

            if (!file.exists()) return null

            val lines = file.readLines()

            if (lines.size <= 1) return null   // header only

            val lastLine = lines.last()

            val parts = lastLine.split(",")

            return BehaviorSignals(
                timestamp = parts[0].toLong(),
                x1_unlock_freq = parts[1].toFloat(),
                x2_micro_session_ratio = parts[2].toFloat(),
                x3_notification_reflex = parts[3].toFloat(),
                x4_behavior_drift_z = parts[4].toFloat(),
                x5_time_phase = parts[5].toFloat(),
                x6_vad_energy = parts[6].toFloat(),
                x7_ble_social = parts[7].toFloat()
            )

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }
}