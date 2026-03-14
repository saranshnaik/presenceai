package com.nophubbing.presenceai.ml

/**
 * FeatureEngineering.kt — maps a SignalRow to a 14-element FeatureVector.
 * Pure object. No IO. No Context. No throws for expected inputs.
 *
 * Normalisation ranges match the training CSV distribution.
 * Changing the order of features breaks the dot product — NEVER reorder.
 *
 * NOTE on BLE / VAD gates:
 *   x10 voice_activity_detected  → hardware binary: 0 until mic permission
 *   x11 people_nearby_count      → converted to binary gate: 0 until BT granted
 *   x12 vad_confidence_score     → 0.0 until mic granted
 *   x13 bt_signal_strength       → 0.0 until BT granted
 * When these are 0, the model still works — it just can't confirm social context.
 * The model was trained on this distribution, so it handles it correctly.
 */
object FeatureEngineering {

    fun buildFeatureVector(row: SignalRow): FeatureVector {
        val f = listOf(
            // x0: hour_of_day normalised to [0,1]
            row.hourOfDay.toDouble().coerceIn(0.0, 23.0) / 23.0,

            // x1: is_evening_session — binary 0/1
            row.isEveningSession.toDouble().coerceIn(0.0, 1.0),

            // x2: baseline_unlocks_per_hour — normalised [0, 30]
            row.baselineUnlocksPerHour.coerceIn(0.0, 30.0) / 30.0,

            // x3: baseline_session_duration_s — normalised [0, 300]
            row.baselineSessionDurationS.coerceIn(0.0, 300.0) / 300.0,

            // x4: baseline_notif_gap_s — normalised [0, 120]
            row.baselineNotifGapS.coerceIn(0.0, 120.0) / 120.0,

            // x5: unlock_count_per_hour — normalised [0, 30]  ← key predictor
            row.unlockCountPerHour.coerceIn(0.0, 30.0) / 30.0,

            // x6: micro_session_duration_s — normalised [0, 60]
            row.microSessionDurationS.coerceIn(0.0, 60.0) / 60.0,

            // x7: notif_to_unlock_gap_s — normalised [0, 60]
            row.notifToUnlockGapS.coerceIn(0.0, 60.0) / 60.0,

            // x8: behavior_drift_score — already a z-score, normalise [-3, 3] → [-1, 1]
            row.behaviorDriftScore.coerceIn(-3.0, 3.0) / 3.0,

            // x9: time_phase_risk — already in [0, 1]
            row.timePhaseRisk.coerceIn(0.0, 1.0),

            // x10: voice_activity_detected — binary; 0 until mic permission
            if (row.voiceActivityDetected == 1) 1.0 else 0.0,

            // x11: people_nearby_count → binary gate; 0 until BT permission
            if (row.peopleNearbyCount > 0) 1.0 else 0.0,

            // x12: vad_confidence_score — [0, 1]; 0.0 until mic granted
            row.vadConfidenceScore.coerceIn(0.0, 1.0),

            // x13: bt_signal_strength — [0, 1]; 0.0 until BT granted
            row.btSignalStrength.coerceIn(0.0, 1.0)
        )

        // Validate — should never trigger given coerceIn guards above
        f.forEachIndexed { i, v ->
            require(!v.isNaN() && !v.isInfinite()) {
                "Feature[${FEATURE_NAMES[i]}] = $v is NaN or Inf for row at ${row.timestamp}"
            }
        }

        return FeatureVector(f)
    }
}
