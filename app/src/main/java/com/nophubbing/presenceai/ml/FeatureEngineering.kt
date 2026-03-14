package com.nophubbing.presenceai.ml

import kotlin.math.max
import kotlin.math.min

object FeatureEngineering {

    /**
     * Directly map from SignalRow to a 14-element feature vector
     * aligned to the FEATURE_NAMES list in Schema.kt:
     *   hour_of_day, is_evening_session, baseline_unlocks_per_hour,
     *   baseline_session_duration_s, baseline_notif_gap_s, unlock_count_per_hour,
     *   micro_session_duration_s, notif_to_unlock_gap_s, behavior_drift_score,
     *   time_phase_risk, voice_activity_detected, people_nearby_count,
     *   vad_confidence_score, bt_signal_strength
     */
    fun buildFeatureVector(row: SignalRow): FeatureVector {
        val rawFeatures = listOf(
            row.hourOfDay.toDouble().coerce(0.0, 23.0) / 23.0,            // normalized hour
            row.isEveningSession.toDouble(),                               // 0 or 1
            row.baselineUnlocksPerHour.coerce(0.0, 30.0) / 30.0,         // normalized baseline
            row.baselineSessionDurationS.coerce(0.0, 300.0) / 300.0,     // normalized session dur
            row.baselineNotifGapS.coerce(0.0, 120.0) / 120.0,            // normalized notif gap
            row.unlockCountPerHour.coerce(0.0, 30.0) / 30.0,             // normalized unlock count
            row.microSessionDurationS.coerce(0.0, 60.0) / 60.0,          // normalized micro session
            row.notifToUnlockGapS.coerce(0.0, 60.0) / 60.0,              // normalized notif->unlock
            row.behaviorDriftScore.coerce(-3.0, 3.0) / 3.0,              // normalized drift
            row.timePhaseRisk.coerce(0.0, 1.0),                           // already in [0,1]
            if (row.voiceActivityDetected == 1) 1.0 else 0.0,             // hardcoded 0
            if (row.peopleNearbyCount > 0) 1.0 else 0.0,                  // hardcoded 0
            row.vadConfidenceScore.coerce(0.0, 1.0),                      // hardcoded 0.0
            row.btSignalStrength.coerce(0.0, 1.0)                         // hardcoded 0.0
        )

        for ((i, f) in rawFeatures.withIndex()) {
            if (f.isNaN() || f.isInfinite()) {
                throw IllegalArgumentException("Feature[$i] = $f is NaN or Inf")
            }
        }

        return FeatureVector(rawFeatures)
    }

    private fun Double.coerce(min: Double, max: Double) = kotlin.math.max(min, kotlin.math.min(this, max))
}
