package com.nophubbing.presenceai.ml

/**
 * FeatureEngineering.kt — maps SignalRow to a 14-feature vector for inference.
 * Matches the schema defined in Schema.kt.
 */
object FeatureEngineering {

    /**
     * Maps superset SignalRow to 7-feature vector for RL inference.
     * Idx 0: x1 unlock_freq (normalized)
     * Idx 1: x2 micro_session_ratio
     * Idx 2: x3 notification_reflex
     * Idx 3: x4 behavior_drift_z
     * Idx 4: x5 time_phase (hour/is_evening)
     * Idx 5: x6 vad_energy
     * Idx 6: x7 ble_social
     */
    fun buildFeatureVector(row: SignalRow): FloatArray {
        val features = FloatArray(7)

        // x1 Unlock Frequency (normalized)
        val denom = if (row.rollingAvgUnlocks < 0.1f) 3.0f else row.rollingAvgUnlocks
        features[0] = row.unlocks10Min / denom

        // x2 Micro-session ratio
        features[1] = if (row.totalSessions > 0) row.microSessions.toFloat() / row.totalSessions else 0f

        // x3 Notification Reflex (simplified)
        features[2] = if (row.lastNotifDeltaMs < 60_000) 1.0f else 0.0f

        // x4 Behavior Drift Z-Score
        features[3] = if (row.baselineReady) {
            (row.behaviorRate - row.baselineMean) / row.baselineStdDev.coerceAtLeast(0.01f)
        } else 0.0f

        // x5 Time Phase (0=Day, 1=Night/Evening)
        features[4] = if (row.hourOfDay >= 18 || row.hourOfDay < 6) 1.0f else 0.0f

        // x6 VAD Energy (binary for this model)
        features[5] = row.voiceActivityDetected.toFloat()

        // x7 BLE Social Context (nearby count)
        features[6] = row.peopleNearbyCount.toFloat()

        return features
    }
}
