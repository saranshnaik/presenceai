package com.nophubbing.presenceai.ml

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

object LrClassifier {

    /**
     * Numerically stable sigmoid, strictly in (0.0, 1.0) for any finite input.
     */
    fun sigmoid(x: Double): Double {
        val clamped = max(-36.0, min(x, 36.0))
        return 1.0 / (1.0 + exp(-clamped))
    }

    /**
     * Computes sum of element-wise products.
     */
    fun dotProduct(features: List<Double>, weights: List<Double>): Double {
        if (features.size != weights.size) {
            throw IllegalArgumentException("Length mismatch: features(${features.size}) vs weights(${weights.size})")
        }

        var result = 0.0
        for (i in features.indices) {
            val f = features[i]
            val w = weights[i]
            if (f.isNaN() || f.isInfinite() || w.isNaN() || w.isInfinite()) {
                throw IllegalArgumentException("NaN or Inf detected in dot product inputs")
            }
            result += f * w
        }

        return result
    }

    /**
     * P(drift) = sigmoid(dot(features, weights) + bias)
     * Returns float in [0.0, 1.0].
     */
    fun predict(features: List<Double>, weights: LRWeights): Double {
        val z = dotProduct(features, weights.asList()) + weights.bias
        return sigmoid(z)
    }

    /**
     * Applies nudge gate formula on top of P(drift).
     * Multiplication acts as a structural hard gate for x7_ble.
     */
    fun computePPhub(features: List<Double>, weights: LRWeights, config: PipelineConfig): Double {
        val pDrift = predict(features, weights)

        // x6_vad = index 5, x7_ble = index 6
        val x6Vad = features[5]
        val x7Ble = features[6]

        val vadMult = if (x6Vad == 1.0) config.vad_multiplier else 1.0
        val pPhub = pDrift * x7Ble * vadMult

        return max(0.0, min(pPhub, 1.0))
    }

    /**
     * Evaluates if the final probability exceeds the intervention threshold.
     */
    fun shouldNudge(features: List<Double>, weights: LRWeights, config: PipelineConfig): Boolean {
        return computePPhub(features, weights, config) > config.nudge_threshold
    }
}
