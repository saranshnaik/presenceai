package com.nophubbing.presenceai.ml

import java.util.Date

/**
 * Schema.kt — Core data types for the phubbing detection pipeline.
 * Single source of truth. No merge conflicts. No duplicate PipelineConfig.
 */

data class SignalRow(
    val timestamp: Long = System.currentTimeMillis(),
    val userId: Int = 1,
    val dayNumber: Int = 1,
    val hourOfDay: Int = 0,
    val isEveningSession: Int = 0,
    // Legacy 14-field analytics (for CSV compatibility)
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
    // 7-feature pipeline fields
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
            w            = PipelineConfig.DEFAULT_WEIGHTS.map { it.toDouble() },
            bias         = PipelineConfig.DEFAULT_BIAS.toDouble(),
            update_count = 0,
            last_updated = Date().toString()
        )
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
