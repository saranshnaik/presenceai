package com.nophubbing.presenceai.ml

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

object LrClassifier {

    /** Numerically stable sigmoid, strictly in (0.0, 1.0). */
    fun sigmoid(x: Double): Double {
        val clamped = max(-500.0, min(x, 500.0))
        return 1.0 / (1.0 + exp(-clamped))
    }

    /** Dot product of feature and weight vectors. */
    fun dotProduct(features: List<Double>, weights: List<Double>): Double {
        if (features.size != weights.size) {
            throw IllegalArgumentException("Length mismatch: features(${features.size}) vs weights(${weights.size})")
        }
        var result = 0.0
        for (i in features.indices) {
            val f = features[i]
            val w = weights[i]
            if (f.isNaN() || f.isInfinite() || w.isNaN() || w.isInfinite()) {
                throw IllegalArgumentException("NaN or Inf in dot product at index $i")
            }
            result += f * w
        }
        return result
    }

    /**
     * P(drift) = sigmoid(dot(features, weights) + bias)
     */
    fun predict(features: List<Double>, weights: LRWeights): Double {
        val z = dotProduct(features, weights.asList()) + weights.bias
        return sigmoid(z)
    }

    /**
     * P(phub) ≈ P(drift). Since voice and BLE are forced to 0, the original
     * multiplicative gate always returns 0, so we use P(drift) directly as P(phub).
     */
    fun computePPhub(features: List<Double>, weights: LRWeights, config: PipelineConfig): Double {
        val pDrift = predict(features, weights)
        return max(0.0, min(pDrift, 1.0))
    }

    /** Returns true when P(phub) exceeds the nudge threshold. */
    fun shouldNudge(features: List<Double>, weights: LRWeights, config: PipelineConfig): Boolean {
        return computePPhub(features, weights, config) > config.nudge_threshold
    }
}
