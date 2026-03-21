package com.nophubbing.presenceai.ml

/**
 * PipelineConfig.kt — ALL constants (single source of truth).
 */
object PipelineConfig {
    const val NUDGE_THRESHOLD = 0.5f
    const val LEARNING_RATE = 0.01f
    const val VAD_MULTIPLIER = 1.15f
    
    // Bandit constants
    const val EPSILON = 0.20f
    const val MIN_TRIALS_PER_ARM = 5
    
    // Timing and Observation
    const val OBSERVATION_WINDOW_S = 45
    const val POSITIVE_LABEL_THRESHOLD_S = 45
    const val BANDIT_FULL_REWARD_S = 45
    const val BANDIT_PARTIAL_REWARD_S = 20
    const val RE_UNLOCK_WINDOW_MS = 60_000L
    const val DISMISS_THRESHOLD_MS = 3_000L
    
    // Baseline Adaptive Model
    const val MIN_SAMPLES_FOR_BASELINE = 72
    const val MIN_STDDEV_FOR_DRIFT = 0.1f
    const val EWMA_ALPHA = 0.05f
    
    // Nudge Gates
    const val MAX_NUDGES_PER_DAY = 8
    const val KILL_SWITCH_DURATION_MS = 900_000L
    
    // Default weights (from android_weights.json)
    val DEFAULT_WEIGHTS = floatArrayOf(0.85f, 0.60f, 0.90f, 0.00f, 0.70f, 0.80f, 0.85f)
    const val DEFAULT_BIAS = -2.50f

    val FEATURE_NAMES = listOf(
        "x1_unlock_freq",
        "x2_micro_session_ratio",
        "x3_notification_reflex",
        "x4_behavior_drift_z",
        "x5_time_phase",
        "x6_vad_energy",
        "x7_ble_social"
    )
}
