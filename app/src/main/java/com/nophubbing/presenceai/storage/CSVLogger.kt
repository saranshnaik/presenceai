package com.nophubbing.presenceai.storage

import android.content.Context
import android.util.Log
import com.nophubbing.presenceai.analytics.BehaviorSignals
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CSVLogger — appends one row per heartbeat to presenceai_dataset.csv.
 *
 * Extended column set (27 columns) for full GenAI compatibility.
 * All fields required by NudgeContext, WeeklyStats, NudgeEvent are stored here.
 *
 * Column index reference:
 *  0  timestamp_iso          — ISO-8601 wall-clock time
 *  1  user_id
 *  2  day_number
 *  3  hour_of_day
 *  4  is_evening_session
 *  5  baseline_unlocks_per_hour
 *  6  baseline_session_duration_s
 *  7  baseline_notif_gap_s
 *  8  unlock_count_per_hour
 *  9  micro_session_duration_s
 *  10 notif_to_unlock_gap_s
 *  11 behavior_drift_score
 *  12 time_phase_risk
 *  13 voice_activity_detected
 *  14 people_nearby_count
 *  15 vad_confidence_score
 *  16 bt_signal_strength
 *  17 raw_unlock_count       — actual unlocks in the 10-min window (not extrapolated)
 *  18 total_sessions         — raw app-switch count
 *  19 micro_session_count    — sessions < 20 s
 *  20 notif_reflex_count     — sessions < 5 s
 *  21 micro_session_ratio    — micro_session_count / total_sessions
 *  22 notif_reflex_ratio     — notif_reflex_count / total_sessions
 *  23 P_drift                — model output (backfilled after 45s)
 *  24 presence_score         — (1 - P_phub) * 100 (backfilled)
 *  25 nudge_sent             — 1 if a nudge fired this heartbeat
 *  26 user_response          — none / put_down / re_unlock / dismissed
 *  27 is_phubbing            — resolved label: 1 / 0 / -1 (unknown)
 *  28 nudge_count_today      — cumulative nudges sent today at time of row
 */
class CSVLogger(private val context: Context) {

    private val fileName = "presenceai_dataset.csv"
    private val isoFmt   = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

    // Tracks nudges sent today; resets at midnight
    private var nudgeCountToday  = 0
    private var lastNudgeDateStr = ""

    private val header =
        "timestamp_iso,user_id,day_number,hour_of_day,is_evening_session," +
        "baseline_unlocks_per_hour,baseline_session_duration_s,baseline_notif_gap_s," +
        "unlock_count_per_hour,micro_session_duration_s,notif_to_unlock_gap_s," +
        "behavior_drift_score,time_phase_risk,voice_activity_detected,people_nearby_count," +
        "vad_confidence_score,bt_signal_strength," +
        "raw_unlock_count,total_sessions,micro_session_count,notif_reflex_count," +
        "micro_session_ratio,notif_reflex_ratio," +
        "P_drift,presence_score,nudge_sent,user_response,is_phubbing,nudge_count_today\n"

    fun logSignals(signals: BehaviorSignals) {
        try {
            // Reset daily nudge counter at midnight
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            if (today != lastNudgeDateStr) {
                nudgeCountToday  = 0
                lastNudgeDateStr = today
            }
            if (signals.nudgeSent == 1) nudgeCountToday++

            val file  = File(context.filesDir, fileName)
            val isNew = !file.exists() || file.length() == 0L
            FileWriter(file, true).use { w ->
                if (isNew) w.append(header)
                w.append(buildRow(signals))
            }
        } catch (e: Exception) {
            Log.e("PresenceAI", "CSV write failed: ${e.message}")
        }
    }

    private fun buildRow(s: BehaviorSignals): String {
        val ts            = isoFmt.format(Date())
        val microRatio    = if (s.totalSessions > 0) s.microSessions.toFloat() / s.totalSessions else 0f
        val reflexRatio   = if (s.totalSessions > 0) s.notificationReflexCount.toFloat() / s.totalSessions else 0f
        return "$ts,${s.userId},${s.dayNumber},${s.hourOfDay},${s.isEveningSession}," +
               "${s.baselineUnlocksPerHour},${s.baselineSessionDurationS},${s.baselineNotifGapS}," +
               "${s.unlockCountPerHour},${s.microSessionDurationS},${s.notifToUnlockGapS}," +
               "${s.behaviorDriftScore},${s.timePhaseRisk},${s.voiceActivityDetected},${s.peopleNearbyCount}," +
               "${s.vadConfidenceScore},${s.btSignalStrength}," +
               "${s.unlocks},${s.totalSessions},${s.microSessions},${s.notificationReflexCount}," +
               "${"%.4f".format(microRatio)},${"%.4f".format(reflexRatio)}," +
               "${s.pDrift},${s.presenceScore},${s.nudgeSent},${s.userResponse},${s.isPhubbing},$nudgeCountToday\n"
    }

    /**
     * Backfill the resolved label and ML outputs into the most recent row.
     * Called from DashboardViewModel.onLabelResolved() after the 45s observation window.
     * Col indices (0-based): 23=P_drift, 24=presence_score, 27=is_phubbing
     */
    fun updateLastRowLabel(label: Double, pDrift: Float, presenceScore: Float) {
        try {
            val file = File(context.filesDir, fileName)
            if (!file.exists()) return
            val lines = file.readLines().toMutableList()
            if (lines.size < 2) return

            val lastIdx = lines.lastIndex
            val parts   = lines[lastIdx].split(",").toMutableList()
            if (parts.size < 28) return

            parts[23] = pDrift.toString()
            parts[24] = presenceScore.toString()
            parts[27] = when {
                label == 1.0  -> "1"
                label == 0.0  -> "0"
                else          -> parts[27]   // -1.0 ambiguous → keep as-is
            }
            lines[lastIdx] = parts.joinToString(",")

            val tmp = File(context.filesDir, "$fileName.tmp")
            tmp.writeText(lines.joinToString("\n") + "\n")
            tmp.renameTo(file)
        } catch (e: Exception) {
            Log.e("PresenceAI", "CSVLogger.updateLastRowLabel failed: ${e.message}")
        }
    }

    /** Returns the current count of nudges sent today (used by GenAI context builder). */
    fun getNudgeCountToday(): Int = nudgeCountToday
}
