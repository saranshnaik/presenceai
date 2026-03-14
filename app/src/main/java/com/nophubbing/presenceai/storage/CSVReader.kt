package com.nophubbing.presenceai.storage

import android.content.Context
import com.nophubbing.presenceai.analytics.BehaviorSignals
import com.nophubbing.presenceai.ml.SignalRow
import java.io.File

object CSVReader {

    /**
     * Reads all rows from CSV and maps into SignalRow for batch training.
     * Columns: user_id(0), day_number(1), hour_of_day(2), is_evening_session(3),
     *   baseline_unlocks_per_hour(4), baseline_session_duration_s(5), baseline_notif_gap_s(6),
     *   unlock_count_per_hour(7), micro_session_duration_s(8), notif_to_unlock_gap_s(9),
     *   behavior_drift_score(10), time_phase_risk(11), voice_activity_detected(12),
     *   people_nearby_count(13), vad_confidence_score(14), bt_signal_strength(15),
     *   P_drift(16), presence_score(17), nudge_sent(18), user_response(19), is_phubbing(20)
     */
    fun readAllAsSignalRows(context: Context): List<SignalRow> {
        val rows = mutableListOf<SignalRow>()
        try {
            val file = File(context.filesDir, "presenceai_dataset.csv")
            if (!file.exists()) return emptyList()

            val lines = file.readLines()
            if (lines.size <= 1) return emptyList()

            for (i in 1 until lines.size) {
                val parts = lines[i].split(",")
                if (parts.size < 21) continue

                try {
                    rows.add(
                        SignalRow(
                            timestamp = System.currentTimeMillis() - (lines.size - i) * 60_000L,
                            userId = parts[0].trim().toIntOrNull() ?: 1,
                            dayNumber = parts[1].trim().toIntOrNull() ?: 1,
                            hourOfDay = parts[2].trim().toIntOrNull() ?: 0,
                            isEveningSession = parts[3].trim().toIntOrNull() ?: 0,
                            baselineUnlocksPerHour = parts[4].trim().toDoubleOrNull() ?: 3.6,
                            baselineSessionDurationS = parts[5].trim().toDoubleOrNull() ?: 52.0,
                            baselineNotifGapS = parts[6].trim().toDoubleOrNull() ?: 21.1,
                            unlockCountPerHour = parts[7].trim().toDoubleOrNull() ?: 0.0,
                            microSessionDurationS = parts[8].trim().toDoubleOrNull() ?: 0.0,
                            notifToUnlockGapS = parts[9].trim().toDoubleOrNull() ?: 0.0,
                            behaviorDriftScore = parts[10].trim().toDoubleOrNull() ?: 0.0,
                            timePhaseRisk = parts[11].trim().toDoubleOrNull() ?: 0.4,
                            voiceActivityDetected = 0,   // forced to 0
                            peopleNearbyCount = 0,        // forced to 0
                            vadConfidenceScore = 0.0,     // forced to 0.0
                            btSignalStrength = 0.0,       // forced to 0.0
                            label = parts[20].trim().toDoubleOrNull() ?: -1.0
                        )
                    )
                } catch (e: Exception) {
                    android.util.Log.w("PresenceAI", "Skipping malformed CSV row $i: ${e.message}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PresenceAI", "CSV read failed", e)
        }
        return rows
    }

    fun readLatestSignals(context: Context): BehaviorSignals? {
        try {
            val file = File(context.filesDir, "presenceai_dataset.csv")
            if (!file.exists()) return null

            val lines = file.readLines()
            if (lines.size <= 1) return null

            val lastLine = lines.last()
            val parts = lastLine.split(",")
            if (parts.size < 21) return null

            return BehaviorSignals(
                userId = parts[0].trim().toIntOrNull() ?: 1,
                dayNumber = parts[1].trim().toIntOrNull() ?: 1,
                hourOfDay = parts[2].trim().toIntOrNull() ?: 0,
                isEveningSession = parts[3].trim().toIntOrNull() ?: 0,
                baselineUnlocksPerHour = parts[4].trim().toFloatOrNull() ?: 3.6f,
                baselineSessionDurationS = parts[5].trim().toFloatOrNull() ?: 52.0f,
                baselineNotifGapS = parts[6].trim().toFloatOrNull() ?: 21.1f,
                unlockCountPerHour = parts[7].trim().toFloatOrNull() ?: 0.0f,
                microSessionDurationS = parts[8].trim().toFloatOrNull() ?: 0.0f,
                notifToUnlockGapS = parts[9].trim().toFloatOrNull() ?: 0.0f,
                behaviorDriftScore = parts[10].trim().toFloatOrNull() ?: 0.0f,
                timePhaseRisk = parts[11].trim().toFloatOrNull() ?: 0.4f,
                voiceActivityDetected = 0,
                peopleNearbyCount = 0,
                vadConfidenceScore = 0.0f,
                btSignalStrength = 0.0f,
                pDrift = parts[16].trim().toFloatOrNull() ?: 0.0f,
                presenceScore = parts[17].trim().toFloatOrNull() ?: 100.0f,
                nudgeSent = parts[18].trim().toIntOrNull() ?: 0,
                userResponse = parts[19].trim(),
                isPhubbing = parts[20].trim().toIntOrNull() ?: 0,
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            android.util.Log.e("PresenceAI", "CSV latest read failed", e)
        }
        return null
    }
}

