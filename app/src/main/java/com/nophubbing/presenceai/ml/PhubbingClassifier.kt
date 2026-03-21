package com.nophubbing.presenceai.ml

import kotlin.math.exp

/**
 * PhubbingClassifier.kt — Logistic Regression (LR) for phubbing detection.
 * Pure math. No IO.
 *
 * All index orders are fixed as per project context:
 * 0: x1 unlock_freq
 * 1: x2 micro_session_ratio
 * 2: x3 notification_reflex
 * 3: x4 behavior_drift_z
 * 4: x5 time_phase
 * 5: x6 vad_energy
 * 6: x7 ble_social
 */
object PhubbingClassifier {

    /**
     * sigmoid(x) = 1 / (1 + exp(-x))
     * Clamped to avoid overflow.
     */
    private fun sigmoid(x: Float): Float {
        val clamped = x.coerceIn(-500f, 500f)
        return 1f / (1f + exp(-clamped))
    }

    /**
     * P(drift) = sigmoid(dot(features, weights) + bias)
     */
    fun computePDrift(features: FloatArray, weights: FloatArray, bias: Float): Float {
        require(features.size == 7) { "Expected 7 features" }
        require(weights.size == 7) { "Expected 7 weight values" }
        
        var dotProduct = zeroSum(features, weights)
        return sigmoid(dotProduct + bias)
    }

    private fun zeroSum(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in a.indices) {
            sum += a[i] * b[i]
        }
        return sum
    }

    /**
     * P(phub) = (P(drift) × x7 × if(x6==1f) 1.15f else 1f).coerceIn(0f, 1f)
     */
    fun computePPhub(features: FloatArray, weights: FloatArray, bias: Float): Float {
        val pDrift = computePDrift(features, weights, bias)
        val x6Vad = features[5]
        val x7Ble = features[6]
        
        val vadMult = if (x6Vad == 1f) PipelineConfig.VAD_MULTIPLIER else 1f
        return (pDrift * x7Ble * vadMult).coerceIn(0f, 1f)
    }

    /**
     * shouldNudge = P(phub) > 0.65f
     */
    fun shouldNudge(pPhub: Float): Boolean {
        return pPhub > PipelineConfig.NUDGE_THRESHOLD
    }

    /**
     * Online learning — one step per labeled interaction.
     * w_new[i] = w_old[i] + 0.01f × error × features[i]
     * bias_new = bias_old + 0.01f × error
     */
    fun updateWeights(
        features: FloatArray,
        label: Float,
        currentWeights: FloatArray,
        currentBias: Float,
        pDrift: Float
    ): Pair<FloatArray, Float> {
        // Label -1.0 means ambiguous, return current weights unchanged
        if (label == -1.0f) return Pair(currentWeights, currentBias)

        val error = label - pDrift
        val learningRate = PipelineConfig.LEARNING_RATE

        val newWeights = FloatArray(7)
        for (i in 0 until 7) {
            newWeights[i] = currentWeights[i] + learningRate * error * features[i]
        }
        val newBias = currentBias + learningRate * error
        
        return Pair(newWeights, newBias)
    }
}
