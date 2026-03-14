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
    // private const val SOCIAL_ABSENT_SCALE = 0.7

    /* 
    // ML logic commented out as requested to use Rule-Based detection instead
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
            result += features[i] * weights[i]
        }
        return result
    }

    fun predict(features: List<Double>, weights: LRWeights): Double {
        val z = dotProduct(features, weights.asList()) + weights.bias
        return sigmoid(z)
    }
    */

    /**
     * P(phub) — Now using Rule-Based Heuristics ("Damn Perfect" Mode)
     */
    fun computePPhub(features: List<Double>, weights: LRWeights, config: PipelineConfig): Double {
        return RuleBasedClassifier.computePPhub(features)
    }

    fun shouldNudge(features: List<Double>, weights: LRWeights, config: PipelineConfig): Boolean {
        val pPhub = computePPhub(features, weights, config)
        return RuleBasedClassifier.shouldNudge(pPhub, config.nudge_threshold)
    }

    /** 
     * predict() maps to pDrift in heuristic mode.
     */
    fun predict(features: List<Double>, weights: LRWeights): Double {
        return RuleBasedClassifier.computePDrift(features)
    }
}
