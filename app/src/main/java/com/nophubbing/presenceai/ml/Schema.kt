package com.nophubbing.presenceai.ml

import java.util.Date

val FEATURE_NAMES = listOf(
    "hour_of_day",
    "is_evening_session",
    "baseline_unlocks_per_hour",
    "baseline_session_duration_s",
    "baseline_notif_gap_s",
    "unlock_count_per_hour",
    "micro_session_duration_s",
    "notif_to_unlock_gap_s",
    "behavior_drift_score",
    "time_phase_risk",
    "voice_activity_detected",
    "people_nearby_count",
    "vad_confidence_score",
    "bt_signal_strength"
)

val DEFAULT_WEIGHTS = List(FEATURE_NAMES.size) { 0.1 }

val TIME_PHASE_TABLE = mapOf(
    Pair(0, 5) to 0.2,
    Pair(6, 8) to 0.3,
    Pair(9, 11) to 0.5,
    Pair(12, 14) to 0.6,
    Pair(15, 17) to 0.5,
    Pair(18, 19) to 0.7,
    Pair(20, 22) to 0.9,
    Pair(23, 23) to 0.6
)

data class SignalRow(
    val timestamp: Long,
    val userId: Int,
    val dayNumber: Int,
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

data class FeatureVector(
    val features: List<Double>
) {
    fun asList(): List<Double> {
        return features
    }
}

data class LRWeights(
    val w: List<Double>,
    val bias: Double,
    val update_count: Int,
    val last_updated: String
) {
    fun asList(): List<Double> {
        return w
    }

    companion object {
        fun defaults(): LRWeights {
            return LRWeights(
                w = DEFAULT_WEIGHTS,
                bias = -2.50,
                update_count = 0,
                last_updated = Date().toString()
            )
        }
    }
}

data class TrainingStep(
    val step: Int,
    val p_drift: Double,
    val p_phub: Double,
    val should_nudge: Boolean,
    val label: Double,
    val error: Double?,
    val weights_snapshot: List<Double>
)

data class PipelineConfig(
    val nudge_threshold: Double = 0.65,
    val lr_learning_rate: Double = 0.01,
    val vad_multiplier: Double = 1.15,
    val max_nudges_per_day: Int = 8,
    val random_seed: Int = 42,
    val log_every_n_steps: Int = 10
)
