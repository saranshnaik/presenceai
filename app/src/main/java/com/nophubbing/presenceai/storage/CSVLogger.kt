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
            val isNewFile = !file.exists() || file.length() == 0L

            FileWriter(file, true).use { writer ->

                if (isNewFile) {
                    writer.append(
                        "user_id,day_number,hour_of_day,is_evening_session," +
                                "baseline_unlocks_per_hour,baseline_session_duration_s,baseline_notif_gap_s," +
                                "unlock_count_per_hour,micro_session_duration_s,notif_to_unlock_gap_s," +
                                "behavior_drift_score,time_phase_risk,voice_activity_detected," +
                                "people_nearby_count,vad_confidence_score,bt_signal_strength," +
                                "P_drift,presence_score,nudge_sent,user_response,is_phubbing\n"
                    )
                }

                writer.append(
                    "${signals.userId}," +
                            "${signals.dayNumber}," +
                            "${signals.hourOfDay}," +
                            "${signals.isEveningSession}," +
                            "${signals.baselineUnlocksPerHour}," +
                            "${signals.baselineSessionDurationS}," +
                            "${signals.baselineNotifGapS}," +
                            "${signals.unlockCountPerHour}," +
                            "${signals.microSessionDurationS}," +
                            "${signals.notifToUnlockGapS}," +
                            "${signals.behaviorDriftScore}," +
                            "${signals.timePhaseRisk}," +
                            "${signals.voiceActivityDetected}," +
                            "${signals.peopleNearbyCount}," +
                            "${signals.vadConfidenceScore}," +
                            "${signals.btSignalStrength}," +
                            "${signals.pDrift}," +
                            "${signals.presenceScore}," +
                            "${signals.nudgeSent}," +
                            "${signals.userResponse}," +
                            "${signals.isPhubbing}\n"
                )
            }

        } catch (e: Exception) {
            Log.e("PresenceAI", "CSV write failed", e)
        }
    }
}