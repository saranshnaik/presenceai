package com.nophubbing.presenceai.integration

import com.nophubbing.presenceai.ml.PhubbingClassifier
import com.nophubbing.presenceai.rl.ResolvedLabels

/**
 * LearningCycle — observation → LR update + bandit update.
 */
object LearningCycle {

    fun run(
        features: FloatArray,
        pDrift: Float,
        weights: FloatArray,
        bias: Float,
        resolved: ResolvedLabels
    ): Pair<FloatArray, Float> {
        // LR Update
        val (newWeights, newBias) = PhubbingClassifier.updateWeights(
            features,
            resolved.lrLabel.toFloat(),
            weights,
            bias,
            pDrift
        )

        // Bandit Update is handled by BanditStore/Bandit.update separately 
        // as BanditState is in SharedPreferences.
        // We return the new weights/bias for persistence in ModelStore/Room.
        return Pair(newWeights, newBias)
    }
}
