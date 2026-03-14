package com.nophubbing.presenceai.ml

/**
 * FeatureEngineering.kt — maps a SignalRow to a 14-element FeatureVector.
 * Updated to increase sensitivity for high-frequency behavioral signals.
 */
object FeatureEngineering {

    fun buildFeatureVector(row: SignalRow): FeatureVector {
        val f = listOf(
            // x0: hour_of_day normalised to [0,1]
            row.hourOfDay.toDouble().coerceIn(0.0, 23.0) / 23.0,

            // x1: is_evening_session — binary 0/1
            row.isEveningSession.toDouble().coerceIn(0.0, 1.0),

            // x2: baseline_unlocks_per_hour — normalised [0, 20]
            row.baselineUnlocksPerHour.coerceIn(0.0, 20.0) / 20.0,

            // x3: baseline_session_duration_s — normalised [0, 300]
            row.baselineSessionDurationS.coerceIn(0.0, 300.0) / 300.0,

            // x4: baseline_notif_gap_s — normalised [0, 120]
            row.baselineNotifGapS.coerceIn(0.0, 120.0) / 120.0,

            // x5: unlock_count_per_hour — normalised [0, 20] (Lowered range for higher sensitivity)
            row.unlockCountPerHour.coerceIn(0.0, 20.0) / 20.0,

            // x6: micro_session_duration_s — normalised [0, 60]
            row.microSessionDurationS.coerceIn(0.0, 60.0) / 60.0,

            // x7: notif_to_unlock_gap_s — normalised [0, 60]
            row.notifToUnlockGapS.coerceIn(0.0, 60.0) / 60.0,

            // x8: behavior_drift_score — amplified z-score
            (row.behaviorDriftScore * 1.5).coerceIn(-3.0, 3.0) / 3.0,

            // x9: time_phase_risk — already in [0, 1]
            row.timePhaseRisk.coerceIn(0.0, 1.0),

            // x10: voice_activity_detected — binary
            if (row.voiceActivityDetected == 1) 1.0 else 0.0,

            // x11: people_nearby_count → binary gate
            if (row.peopleNearbyCount > 0) 1.0 else 0.0,

            // x12: vad_confidence_score — [0, 1]
            row.vadConfidenceScore.coerceIn(0.0, 1.0),

            // x13: bt_signal_strength — [0, 1]
            row.btSignalStrength.coerceIn(0.0, 1.0)
        )

        return FeatureVector(f)
    }
}
