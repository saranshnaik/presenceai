package com.nophubbing.presenceai.ml

/**
 * FeatureEngineering — maps SignalRow → FloatArray(7) for the 7-feature pipeline.
 *
 * Index | Name                 | Range   | Guard
 *   0   | x1 unlock_freq       | [0,4]   | rollingAvg < 0.1 → denom = 3.0
 *   1   | x2 micro_sess_ratio  | [0,1]   | totalSessions=0 → 0.0
 *   2   | x3 notif_reflex      | {0,1}   | delta=0 → 0.0
 *   3   | x4 behavior_drift_z  | [-3,3]  | !baselineReady → 0.0
 *   4   | x5 time_phase        | [0,1]   | evening=1, day=0
 *   5   | x6 vad_energy        | {0,1}   | binary
 *   6   | x7 ble_social        | {0,1}   | people count binary
 */
object FeatureEngineering {

    fun buildFeatureVector(row: SignalRow): FloatArray {
        val f = FloatArray(7)

        // x1 Unlock frequency normalised to rolling baseline
        val denom = if (row.rollingAvgUnlocks < 0.1f) 3.0f else row.rollingAvgUnlocks
        f[0] = (row.unlocks10Min.toFloat() / denom).coerceIn(0f, 4f)

        // x2 Micro-session ratio
        f[1] = if (row.totalSessions > 0)
            (row.microSessions.toFloat() / row.totalSessions).coerceIn(0f, 1f)
        else 0f

        // x3 Notification reflex (unlocked phone within 60s of notification)
        f[2] = if (row.lastNotifDeltaMs in 1 until 60_000) 1f else 0f

        // x4 Behaviour drift z-score
        f[3] = if (row.baselineReady) {
            val stddev = row.baselineStdDev.coerceAtLeast(0.01f)
            ((row.behaviorRate - row.baselineMean) / stddev).coerceIn(-3f, 3f)
        } else 0f

        // x5 Time phase (1 = evening/night risk window)
        f[4] = if (row.hourOfDay >= 18 || row.hourOfDay < 6) 1f else 0f

        // x6 VAD energy (binary — voice detected nearby)
        f[5] = row.voiceActivityDetected.toFloat().coerceIn(0f, 1f)

        // x7 BLE social context (binary — device detected nearby)
        f[6] = if (row.peopleNearbyCount > 0) 1f else 0f

        return f
    }
}
