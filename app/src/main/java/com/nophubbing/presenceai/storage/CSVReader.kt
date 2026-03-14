package com.nophubbing.presenceai.storage

import android.content.Context
import android.util.Log
import com.nophubbing.presenceai.analytics.BehaviorSignals
import com.nophubbing.presenceai.ml.SignalRow
import java.io.File

/**
 * CSVReader — reads presenceai_dataset.csv and maps rows to SignalRow for the pipeline.
 */
object CSVReader {

    private const val MIN_COLS = 21

    fun readAllAsSignalRows(context: Context): List<SignalRow> {
        val file = File(context.filesDir, "presenceai_dataset.csv")
        val lines: List<String> = if (file.exists() && file.length() > 0) {
            file.readLines()
        } else {
            readBundledLines(context)
        }
        if (lines.size <= 1) return emptyList()
        return lines.drop(1).mapNotNull { parseSignalRow(it) }.also {
            Log.d("PresenceAI", "CSVReader: loaded ${it.size} rows")
        }
    }

    private fun readBundledLines(context: Context): List<String> {
        return try {
            context.assets.open("PRESENCE_AI_56k_FULL.csv")
                .bufferedReader().readLines()
        } catch (e: Exception) {
            Log.d("PresenceAI", "No bundled CSV: ${e.message}")
            emptyList()
        }
    }

    private fun parseSignalRow(line: String): SignalRow? = try {
        val p = line.trim().split(",")
        if (p.size < MIN_COLS) null
        else SignalRow(
            timestamp                = System.currentTimeMillis(),
            userId                   = p[0].toIntOrNull() ?: 1,
            dayNumber                = p[1].toIntOrNull() ?: 1,
            hourOfDay                = p[2].toInt(),
            isEveningSession         = p[3].toInt(),
            baselineUnlocksPerHour   = p[4].toDouble(),
            baselineSessionDurationS = p[5].toDouble(),
            baselineNotifGapS        = p[6].toDouble(),
            unlockCountPerHour       = p[7].toDouble(),
            microSessionDurationS    = p[8].toDouble(),
            notifToUnlockGapS        = p[9].toDouble(),
            behaviorDriftScore       = p[10].toDouble(),
            timePhaseRisk            = p[11].toDouble(),
            voiceActivityDetected    = p[12].toInt(),
            peopleNearbyCount        = p[13].toInt(),
            vadConfidenceScore       = p[14].toDouble(),
            btSignalStrength         = p[15].toDouble(),
            label                    = p[20].toDouble()
        )
    } catch (e: Exception) { null }

    private fun parseBehaviorSignals(line: String): BehaviorSignals? = try {
        val p = line.trim().split(",")
        if (p.size < MIN_COLS) null
        else BehaviorSignals(
            userId                   = p[0].toIntOrNull() ?: 1,
            dayNumber                = p[1].toIntOrNull() ?: 1,
            hourOfDay                = p[2].toInt(),
            isEveningSession         = p[3].toInt(),
            baselineUnlocksPerHour   = p[4].toFloat(),
            baselineSessionDurationS = p[5].toFloat(),
            baselineNotifGapS        = p[6].toFloat(),
            unlockCountPerHour       = p[7].toFloat(),
            microSessionDurationS    = p[8].toFloat(),
            notifToUnlockGapS        = p[9].toFloat(),
            behaviorDriftScore       = p[10].toFloat(),
            timePhaseRisk            = p[11].toFloat(),
            voiceActivityDetected    = p[12].toInt(),
            peopleNearbyCount        = p[13].toInt(),
            vadConfidenceScore       = p[14].toFloat(),
            btSignalStrength         = p[15].toFloat(),
            pDrift                   = p[16].toFloat(),
            presenceScore            = p[17].toFloat(),
            nudgeSent                = p[18].toIntOrNull() ?: 0,
            userResponse             = p[19],
            isPhubbing               = p[20].toInt(),
            microSessionRatio        = 0f,
            notifReflexRatio         = 0f
        )
    } catch (e: Exception) { null }
}
