package com.nophubbing.presenceai.storage

import android.content.Context
import android.util.Log
import com.nophubbing.presenceai.analytics.BehaviorSignals
import java.io.File
import java.io.FileWriter

/**
 * CSVLogger — appends one row per heartbeat to presenceai_dataset.csv.
 *
 * Column order matches PRESENCE_AI_56k_FULL.csv exactly so CSVReader can load it
 * back without index changes:
 *   user_id, day_number, hour_of_day, is_evening_session,
 *   baseline_unlocks_per_hour, baseline_session_duration_s, baseline_notif_gap_s,
 *   unlock_count_per_hour, micro_session_duration_s, notif_to_unlock_gap_s,
 *   behavior_drift_score, time_phase_risk, voice_activity_detected, people_nearby_count,
 *   vad_confidence_score, bt_signal_strength,
 *   P_drift, presence_score, nudge_sent, user_response, is_phubbing
 *
 * NOTE: columns 7-8 are now micro_session_duration_s / notif_to_unlock_gap_s
 *       (duration values matching the 14-feature model) rather than the old ratio fields.
 *       CSVReader.kt reads these same columns at indices 7 and 8 — both are consistent.
 */
class CSVLogger(private val context: Context) {

    private val fileName = "presenceai_dataset.csv"

    private val header = "user_id,day_number,hour_of_day,is_evening_session," +
        "baseline_unlocks_per_hour,baseline_session_duration_s,baseline_notif_gap_s," +
        "unlock_count_per_hour,micro_session_duration_s,notif_to_unlock_gap_s," +
        "behavior_drift_score,time_phase_risk,voice_activity_detected,people_nearby_count," +
        "vad_confidence_score,bt_signal_strength," +
        "P_drift,presence_score,nudge_sent,user_response,is_phubbing\n"

    fun logSignals(signals: BehaviorSignals) {
        try {
            val file     = File(context.filesDir, fileName)
            val isNew    = !file.exists() || file.length() == 0L

            FileWriter(file, /* append = */ true).use { writer ->
                if (isNew) writer.append(header)

                writer.append(buildRow(signals))
            }
        } catch (e: Exception) {
            Log.e("PresenceAI", "CSV write failed: ${e.message}")
        }
    }

    private fun buildRow(s: BehaviorSignals): String =
        "${s.userId}," +
        "${s.dayNumber}," +
        "${s.hourOfDay}," +
        "${s.isEveningSession}," +
        "${s.baselineUnlocksPerHour}," +
        "${s.baselineSessionDurationS}," +
        "${s.baselineNotifGapS}," +
        "${s.unlockCountPerHour}," +
        "${s.microSessionDurationS}," +          // x6 — duration in seconds
        "${s.notifToUnlockGapS}," +              // x7 — duration in seconds
        "${s.behaviorDriftScore}," +
        "${s.timePhaseRisk}," +
        "${s.voiceActivityDetected}," +
        "${s.peopleNearbyCount}," +
        "${s.vadConfidenceScore}," +
        "${s.btSignalStrength}," +
        "${s.pDrift}," +
        "${s.presenceScore}," +
        "${s.nudgeSent}," +
        "${s.userResponse}," +
        "${s.isPhubbing}\n"

    /**
     * Write the resolved label back into the most recent row in the CSV.
     * Called from onLabelResolved() after the 45s observation window.
     * This makes stored rows usable for future training passes.
     *
     * Strategy: read all lines, replace is_phubbing (col 20) in the LAST data row,
     * and also fill pDrift (col 16) with the actual computed value.
     */
    fun updateLastRowLabel(label: Double, pDrift: Float, presenceScore: Float) {
        try {
            val file = File(context.filesDir, fileName)
            if (!file.exists()) return
            val lines = file.readLines().toMutableList()
            if (lines.size < 2) return  // only header, nothing to update

            val lastIdx = lines.lastIndex
            val parts = lines[lastIdx].split(",").toMutableList()
            if (parts.size < 21) return

            // Update col 16 (P_drift), 17 (presence_score), 20 (is_phubbing)
            parts[16] = pDrift.toString()
            parts[17] = presenceScore.toString()
            parts[20] = when {
                label == 1.0  -> "1"
                label == 0.0  -> "0"
                else          -> parts[20]  // ambiguous (-1.0) → leave as-is
            }
            lines[lastIdx] = parts.joinToString(",")

            // Atomic rewrite
            val tmp = File(context.filesDir, "$fileName.tmp")
            tmp.writeText(lines.joinToString("\n") + "\n")
            tmp.renameTo(file)
        } catch (e: Exception) {
            Log.e("PresenceAI", "CSVLogger.updateLastRowLabel failed: ${e.message}")
        }
    }
}