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

val DEFAULT_WEIGHTS: List<Double> = listOf(
    -0.1271, -0.5497, -1.1495, 1.0957, 0.8610, 1.1217, -2.0021,
    -0.6505, 2.2815, 4.8694, 0.1924, 0.6788, -0.1477, 0.1574
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
            bias = -2.50,
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
        const val NUDGE_THRESHOLD = 0.001
        const val LR_LEARNING_RATE = 0.1
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
