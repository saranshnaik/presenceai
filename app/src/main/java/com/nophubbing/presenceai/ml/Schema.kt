package com.nophubbing.presenceai.ml

import java.util.Date

val FEATURE_NAMES = listOf(
    "unlock_freq",           // x1
    "micro_session_ratio",   // x2
    "notification_reflex",   // x3
    "behavior_drift_z",      // x4
    "time_phase",            // x5
    "vad_energy",            // x6
    "ble_social",            // x7
)

val DEFAULT_WEIGHTS = mapOf(
    "w1" to 0.85,
    "w2" to 0.60,
    "w3" to 0.90,
    "w4" to 0.00,
    "w5" to 0.70,
    "w6" to 0.80,
    "w7" to 0.85,
    "bias" to -2.50
)

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
    val unlock_count_10min: Double,
    val rolling_avg_unlock_rate: Double,
    val sessions_under_30s: Int,
    val total_sessions: Int,
    val last_notif_delta_ms: Long,
    val current_unlock_rate: Double,
    val baseline_mean: Double,
    val baseline_stddev: Double,
    val baseline_ready: Int,
    val hour_of_day: Int,
    val vad_energy: Int,
    val ble_device_count: Int,
    val label: Double
)

data class FeatureVector(
    val x1: Double,
    val x2: Double,
    val x3: Double,
    val x4: Double,
    val x5: Double,
    val x6: Double,
    val x7: Double
) {
    fun asList(): List<Double> {
        return listOf(x1, x2, x3, x4, x5, x6, x7)
    }
}

data class LRWeights(
    val w1: Double,
    val w2: Double,
    val w3: Double,
    val w4: Double,
    val w5: Double,
    val w6: Double,
    val w7: Double,
    val bias: Double,
    val update_count: Int,
    val last_updated: String
) {
    fun asList(): List<Double> {
        return listOf(w1, w2, w3, w4, w5, w6, w7)
    }

    companion object {
        fun defaults(): LRWeights {
            return LRWeights(
                w1 = DEFAULT_WEIGHTS["w1"] ?: 0.0,
                w2 = DEFAULT_WEIGHTS["w2"] ?: 0.0,
                w3 = DEFAULT_WEIGHTS["w3"] ?: 0.0,
                w4 = DEFAULT_WEIGHTS["w4"] ?: 0.0,
                w5 = DEFAULT_WEIGHTS["w5"] ?: 0.0,
                w6 = DEFAULT_WEIGHTS["w6"] ?: 0.0,
                w7 = DEFAULT_WEIGHTS["w7"] ?: 0.0,
                bias = DEFAULT_WEIGHTS["bias"] ?: 0.0,
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
