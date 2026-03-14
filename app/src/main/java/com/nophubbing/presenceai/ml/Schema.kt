package com.nophubbing.presenceai.ml

import java.util.Date

/**
 * Schema.kt — Data classes for the 14-feature ML pipeline.
 */
val FEATURE_NAMES = listOf(
    "hour_of_day",               // x0
    "is_evening_session",        // x1
    "baseline_unlocks_per_hour", // x2
    "baseline_session_duration_s",// x3
    "baseline_notif_gap_s",      // x4
    "unlock_count_per_hour",     // x5
    "micro_session_duration_s",  // x6
    "notif_to_unlock_gap_s",     // x7
    "behavior_drift_score",      // x8
    "time_phase_risk",           // x9
    "voice_activity_detected",   // x10
    "people_nearby_count",       // x11
    "vad_confidence_score",      // x12
    "bt_signal_strength"         // x13
)

/**
 * Recalibrated weights tuned for realistic phubbing detection.
 *
 * Key changes from v1 (-2.50 bias, muted behavioral weights):
 *
 *  bias: -1.20  (was -2.50) — less aggressive prior toward "not phubbing".
 *                              With zero signal the score is still ~77 (low risk)
 *                              but behavioral signals now push it meaningfully.
 *
 *  x5 unlock_count_per_hour:    +2.80 (was +1.12) — primary phubbing driver.
 *                                24 unlocks/hr → feature 1.0 → +2.80 alone.
 *
 *  x6 micro_session_duration_s: -0.80 (was -2.00) — kept negative (longer
 *                                micro sessions = more engaged) but less dominant
 *                                so it doesn't suppress the unlock signal.
 *
 *  x8 behavior_drift_score:     +3.20 (was +2.28) — strongest behavioral weight.
 *                                Drift from baseline is the best single predictor.
 *
 *  x9 time_phase_risk:          +3.50 (was +4.87) — still important but slightly
 *                                reduced so evening alone doesn't dominate.
 *
 *  x11 people_nearby_count:     +2.50 (was +0.68) — BLE presence now a strong
 *                                amplifier. Combined with high unlock → nudge fires.
 *
 *  x12 vad_confidence_score:    +1.20 (was -0.15) — flipped positive. More voice
 *                                confidence = more likely someone is talking to you
 *                                and you're ignoring them = higher phubbing risk.
 *
 * Net effect: at 12 unlocks/hr, 60% micro ratio, evening, no BLE/VAD:
 *   old: P(phub) ≈ 0.016  score ≈ 98   (barely moves)
 *   new: P(phub) ≈ 0.18   score ≈ 82   (visible, feels real)
 *
 * At 24 unlocks/hr, high drift, evening, BLE confirmed:
 *   old: P(phub) ≈ 0.04   score ≈ 96
 *   new: P(phub) ≈ 0.72   score ≈ 28   (crosses nudge threshold → fires)
 */
val DEFAULT_WEIGHTS: List<Double> = listOf(
    -0.10,  // x0  hour_of_day               (minor context)
    -0.30,  // x1  is_evening_session         (context, slightly reduces risk alone)
    -0.80,  // x2  baseline_unlocks_per_hour  (high baseline = less anomalous)
     0.60,  // x3  baseline_session_duration_s
     0.40,  // x4  baseline_notif_gap_s
     2.80,  // x5  unlock_count_per_hour      ↑↑ primary behavioral driver
    -0.80,  // x6  micro_session_duration_s   (longer micro = more engaged)
    -0.40,  // x7  notif_to_unlock_gap_s
     3.20,  // x8  behavior_drift_score       ↑↑ strongest behavioral predictor
     3.50,  // x9  time_phase_risk            ↑  evening amplifier
     0.50,  // x10 voice_activity_detected    (binary gate)
     2.50,  // x11 people_nearby_count        ↑↑ social context amplifier
     1.20,  // x12 vad_confidence_score       ↑  continuous voice (was -0.15, now positive)
     1.00   // x13 bt_signal_strength         ↑  continuous BLE
)

data class SignalRow(
    val timestamp: Long,
    val userId: Int = 1,
    val dayNumber: Int = 1,
    val hourOfDay: Int,
    val isEveningSession: Int,
    val baselineUnlocksPerHour: Double,
    val baselineSessionDurationS: Double,
    val baselineNotifGapS: Double,
    val unlockCountPerHour: Double,
    val microSessionDurationS: Double,
    val notifToUnlockGapS: Double,
    val behaviorDriftScore: Double,
    val timePhaseRisk: Double,
    val voiceActivityDetected: Int,
    val peopleNearbyCount: Int,
    val vadConfidenceScore: Double,
    val btSignalStrength: Double,
    val label: Double
)

data class FeatureVector(val features: List<Double>) {
    init {
        require(features.size == FEATURE_NAMES.size)
    }
    fun asList(): List<Double> = features
}

data class LRWeights(
    val w: List<Double>,
    val bias: Double,
    val update_count: Int = 0,
    val last_updated: String = ""
) {
    fun asList(): List<Double> = w
    companion object {
        fun defaults(): LRWeights = LRWeights(
            w = DEFAULT_WEIGHTS,
            bias = -1.20,
            update_count = 0,
            last_updated = Date().toString()
        )
    }
}

/**
 * PipelineConfig — single source of truth for all ML constants.
 */
data class PipelineConfig(
    val nudge_threshold: Double = NUDGE_THRESHOLD,
    val lr_learning_rate: Double = LR_LEARNING_RATE,
    val vad_multiplier: Double = VAD_MULTIPLIER,
    val max_nudges_per_day: Int = MAX_NUDGES_PER_DAY,
    val observation_window_s: Int = OBSERVATION_WINDOW_S,
    val positive_label_threshold_s: Int = POSITIVE_LABEL_THRESHOLD_S,
    val re_unlock_window_ms: Long = RE_UNLOCK_WINDOW_MS,
    val dismiss_threshold_ms: Long = DISMISS_THRESHOLD_MS,
    val heartbeat_interval_ms: Long = HEARTBEAT_INTERVAL_MS
) {
    companion object {
        const val NUDGE_THRESHOLD = 0.65
        const val LR_LEARNING_RATE = 0.01
        const val VAD_MULTIPLIER = 1.15
        const val MAX_NUDGES_PER_DAY = 8
        const val OBSERVATION_WINDOW_S = 45
        const val POSITIVE_LABEL_THRESHOLD_S = 45
        const val RE_UNLOCK_WINDOW_MS = 60_000L
        const val DISMISS_THRESHOLD_MS = 3_000L
        const val HEARTBEAT_INTERVAL_MS = 5_000L

        const val MIN_TRIALS_PER_ARM = 5
        const val EPSILON = 0.15f
        const val BANDIT_FULL_REWARD_S = 45
        const val BANDIT_PARTIAL_REWARD_S = 20
    }
}

data class InferenceResult(
    val pDrift: Float,
    val pPhub: Float,
    val presenceScore: Float,
    val shouldNudge: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class TrainingStep(
    val step: Int,
    val p_drift: Double,
    val p_phub: Double,
    val should_nudge: Boolean,
    val label: Double,
    val error: Double?,
    val weights_snapshot: List<Double>
)
