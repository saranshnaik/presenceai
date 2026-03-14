package com.nophubbing.presenceai.ml

import kotlin.math.exp

/**
 * LrClassifier.kt — core inference for the 14-feature logistic regression.
 * Pure object. No IO.
 *
 * P(drift)  = sigmoid(dot(features[0..13], weights[0..13]) + bias)
 * P(phub)   = P(drift) when social context confirmed (x11 > 0 OR x10 > 0)
 *           = P(drift) * SOCIAL_ABSENT_SCALE when no social context detected
 *             (still shows meaningful score from behavioral signals alone)
 *
 * Why SOCIAL_ABSENT_SCALE instead of 0?
 *   With BT not granted, x11=0 and x13=0, so the regression already reduces
 *   P(drift). But we still want behavioral signals (unlocks, drift, time phase)
 *   to produce a visible score on the UI, especially during demos where the user
 *   can't grant BT mid-presentation. SOCIAL_ABSENT_SCALE = 0.7 means:
 *     - High behavioral activity → P(phub) up to ~0.6 (visible, approaching threshold)
 *     - With social context confirmed → P(phub) up to ~0.86 (crosses threshold, nudges fire)
 *   This makes the score reactive and meaningful at all times.
 *
 * Nudge threshold: 0.65 — only crosses with BOTH high behavior + social context.
 */
object LrClassifier {

    /** Scale applied when no social presence confirmed (BT/VAD not granted or no device found) */
    private const val SOCIAL_ABSENT_SCALE = 0.7

    fun sigmoid(x: Double): Double {
        val clamped = x.coerceIn(-500.0, 500.0)
        return 1.0 / (1.0 + exp(-clamped))
    }

    fun dotProduct(features: List<Double>, weights: List<Double>): Double {
        require(features.size == weights.size) {
            "Length mismatch: features(${features.size}) vs weights(${weights.size})"
        }
        var result = 0.0
        for (i in features.indices) {
            require(!features[i].isNaN() && !weights[i].isNaN()) { "NaN at index $i" }
            result += features[i] * weights[i]
        }
        return result
    }

    /** P(drift) = sigmoid(w·x + b) */
    fun predict(features: List<Double>, weights: LRWeights): Double {
        val z = dotProduct(features, weights.asList()) + weights.bias
        return sigmoid(z)
    }

    /**
     * P(phub) — behavioral probability scaled by social context availability.
     *
     * x10 = voice_activity_detected (index 10)
     * x11 = people_nearby_count     (index 11)
     *
     * Social context present  → full P(drift)
     * Social context absent   → P(drift) * 0.7 (still visible, won't nudge alone)
     */
    fun computePPhub(features: List<Double>, weights: LRWeights, config: PipelineConfig): Double {
        val pDrift = predict(features, weights)
        val socialPresent = features[10] > 0.0 || features[11] > 0.0  // VAD or BLE
        val scale = if (socialPresent) 1.0 else SOCIAL_ABSENT_SCALE
        return (pDrift * scale).coerceIn(0.0, 1.0)
    }

    fun shouldNudge(features: List<Double>, weights: LRWeights, config: PipelineConfig): Boolean {
        return computePPhub(features, weights, config) > config.nudge_threshold
    }
}
