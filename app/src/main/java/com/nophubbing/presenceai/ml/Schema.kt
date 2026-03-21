package com.nophubbing.presenceai.ml

import java.util.Date

/**
 * Schema.kt — Core data structures for the phubbing detection pipeline.
 */

data class SignalRow(
    val timestamp: Long = System.currentTimeMillis(),
    val userId: Int = 1,
    val dayNumber: Int = 1,
    val hourOfDay: Int = 0,
    val isEveningSession: Int = 0,
    
    // 14-field Legacy Analytics properties
    val baselineUnlocksPerHour: Double = 3.6,
    val baselineSessionDurationS: Double = 52.0,
    val baselineNotifGapS: Double = 21.1,
    val unlockCountPerHour: Double = 0.0,
    val microSessionDurationS: Double = 0.0,
    val notifToUnlockGapS: Double = 0.0,
    val behaviorDriftScore: Double = 0.0,
    val timePhaseRisk: Double = 0.0,
    val voiceActivityDetected: Int = 0,
    val peopleNearbyCount: Int = 0,
    val vadConfidenceScore: Double = 0.0,
    val btSignalStrength: Double = 0.0,
    val label: Double = 0.0,

    // 7-feature AI Pipeline properties
    val unlocks10Min: Int = 0,
    val rollingAvgUnlocks: Float = 1.0f,
    val totalSessions: Int = 0,
    val microSessions: Int = 0,
    val lastNotifDeltaMs: Long = 0,
    val behaviorRate: Float = 0f,
    val baselineMean: Float = 0f,
    val baselineStdDev: Float = 0.1f,
    val baselineReady: Boolean = false
)

data class FeatureVector(val features: List<Double>) {
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
            w = PipelineConfig.DEFAULT_WEIGHTS.map { it.toDouble() },
            bias = PipelineConfig.DEFAULT_BIAS.toDouble(),
            update_count = 0,
            last_updated = Date().toString()
        )
    }
}

<<<<<<< HEAD
=======
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
        const val NUDGE_THRESHOLD = 0.5
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

>>>>>>> 812d93c9763a4dcbb68f4ea9d5819da4db7407fb
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
