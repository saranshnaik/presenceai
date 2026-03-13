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
                unlocks = parts[1].toInt(),
                microSessions = parts[2].toInt(),
                notificationReflex = parts[3].toInt(),
                behaviorDrift = parts[4].toFloat(),
                timePhase = parts[5].toInt(),
                voiceDetected = parts[6].toInt(),
                proximityDetected = parts[7].toInt()
            )

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }
}