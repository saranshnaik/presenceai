package com.nophubbing.presenceai.storage

import android.content.Context
import android.util.Log
import com.nophubbing.presenceai.analytics.BehaviorSignals
import com.nophubbing.presenceai.ml.SignalRow
import java.io.File

/**
 * CSVReader — reads presenceai_dataset.csv and maps rows to SignalRow for the pipeline.
 *
 * Supports both schema versions:
 *   Legacy (21 cols): no timestamp_iso prefix — cols 0..20 as before.
 *   Current (29 cols): timestamp_iso at col 0, data starts at col 1.
 *
 * Detection: if p[0] contains '-' (ISO date) → current schema; else → legacy.
 */
object CSVReader {

    private const val MIN_COLS_LEGACY  = 21
    private const val MIN_COLS_CURRENT = 29

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

    private fun parseSignalRow(line: String): SignalRow? {
        return try {
            val p = line.trim().split(",")

            // Detect schema: current schema has ISO timestamp at col 0 (contains '-')
            val isCurrent = p[0].contains("-")
            val o = if (isCurrent) 1 else 0  // offset: column index of user_id

            val minCols = if (isCurrent) MIN_COLS_CURRENT else MIN_COLS_LEGACY
            if (p.size < minCols) return null

            // Label column: current=27, legacy=20
            val labelCol = if (isCurrent) 27 else 20

            SignalRow(
                timestamp                = System.currentTimeMillis(),
                userId                   = p[o + 0].toIntOrNull() ?: 1,
                dayNumber                = p[o + 1].toIntOrNull() ?: 1,
                hourOfDay                = p[o + 2].toInt(),
                isEveningSession         = p[o + 3].toInt(),
                baselineUnlocksPerHour   = p[o + 4].toDouble(),
                baselineSessionDurationS = p[o + 5].toDouble(),
                baselineNotifGapS        = p[o + 6].toDouble(),
                unlockCountPerHour       = p[o + 7].toDouble(),
                microSessionDurationS    = p[o + 8].toDouble(),
                notifToUnlockGapS        = p[o + 9].toDouble(),
                behaviorDriftScore       = p[o + 10].toDouble(),
                timePhaseRisk            = p[o + 11].toDouble(),
                voiceActivityDetected    = p[o + 12].toInt(),
                peopleNearbyCount        = p[o + 13].toInt(),
                vadConfidenceScore       = p[o + 14].toDouble(),
                btSignalStrength         = p[o + 15].toDouble(),
                label                    = p[labelCol].toDoubleOrNull() ?: -1.0
            )
        } catch (e: Exception) { null }
    }
}
