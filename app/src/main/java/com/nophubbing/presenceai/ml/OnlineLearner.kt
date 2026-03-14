package com.nophubbing.presenceai.ml

import java.util.Date
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

object OnlineLearner {

    /**
     * Binary cross-entropy loss for one example.
     */
    fun computeLoss(pDrift: Double, label: Double): Double {
        if (label != 0.0 && label != 1.0) return 0.0

        // Clip to prevent ln(0)
        val p = max(1e-7, min(pDrift, 1.0 - 1e-7))
        return -(label * ln(p) + (1.0 - label) * ln(1.0 - p))
    }

    /**
     * Binary accuracy for one example.
     */
    fun computeAccuracy(pDrift: Double, label: Double, threshold: Double = 0.5): Double {
        if (label != 0.0 && label != 1.0) return 0.0
        val predicted = if (pDrift > threshold) 1.0 else 0.0
        return if (predicted == label) 1.0 else 0.0
    }

    /**
     * One online gradient descent step. Returns a new immutable LRWeights object.
     */
    fun update(features: List<Double>, label: Double, weights: LRWeights, config: PipelineConfig): LRWeights {
        if (label == -1.0) {
            return weights // Return the same object, no updates
        }

        if (label != 0.0 && label != 1.0) {
            throw IllegalArgumentException("Invalid label: $label. Must be 0.0, 1.0, or -1.0")
        }

        val pDrift = LrClassifier.predict(features, weights)
        val error = label - pDrift
        val lr = config.lr_learning_rate

        val oldW = weights.asList()
        val newWeights = oldW.mapIndexed { index, w ->
            w + lr * error * features[index]
        }
        val newBias = weights.bias + lr * error

        return LRWeights(
            w1 = newWeights[0],
            w2 = newWeights[1],
            w3 = newWeights[2],
            w4 = newWeights[3],
            w5 = newWeights[4],
            w6 = newWeights[5],
            w7 = newWeights[6],
            bias = newBias,
            update_count = weights.update_count + 1,
            last_updated = Date().toString()
        )
    }

    fun trainOnBatch(rows: List<SignalRow>, initialWeights: LRWeights, config: PipelineConfig): Pair<LRWeights, List<TrainingStep>> {
        val steps = mutableListOf<TrainingStep>()
        var currentWeights = initialWeights

        for ((i, row) in rows.withIndex()) {
            val fv = FeatureEngineering.buildFeatureVector(row).asList()

            // Inference using PRE-UPDATE weights
            val pDrift = LrClassifier.predict(fv, currentWeights)
            val pPhub = LrClassifier.computePPhub(fv, currentWeights, config)
            val nudge = LrClassifier.shouldNudge(fv, currentWeights, config)

            val error: Double?
            if (row.label == -1.0) {
                error = null
                // Do not update weights
            } else {
                error = row.label - pDrift
                // Create a new weights object
                currentWeights = update(fv, row.label, currentWeights, config)
            }

            val step = TrainingStep(
                step = i,
                p_drift = pDrift,
                p_phub = pPhub,
                should_nudge = nudge,
                label = row.label,
                error = error,
                weights_snapshot = currentWeights.asList()
            )
            steps.add(step)
        }

        return Pair(currentWeights, steps)
    }

    fun computeEpochMetrics(steps: List<TrainingStep>): Map<String, Double> {
        val totalSteps = steps.size
        val labeledSteps = steps.count { it.label != -1.0 }
        val discardedSteps = totalSteps - labeledSteps
        val positiveCount = steps.count { it.label == 1.0 }
        val negativeCount = steps.count { it.label == 0.0 }
        val nudgeCount = steps.count { it.should_nudge }

        var avgLoss = 0.0
        var accuracy = 0.0
        var finalPDriftMean = 0.0

        if (totalSteps > 0) {
            finalPDriftMean = steps.sumOf { it.p_drift } / totalSteps
        }

        if (labeledSteps > 0) {
            val totalLoss = steps.filter { it.label != -1.0 }.sumOf { computeLoss(it.p_drift, it.label) }
            val totalAcc = steps.filter { it.label != -1.0 }.sumOf { computeAccuracy(it.p_drift, it.label) }
            avgLoss = totalLoss / labeledSteps
            accuracy = totalAcc / labeledSteps
        }

        return mapOf(
            "total_steps" to totalSteps.toDouble(),
            "labeled_steps" to labeledSteps.toDouble(),
            "discarded_steps" to discardedSteps.toDouble(),
            "avg_loss" to avgLoss,
            "accuracy" to accuracy,
            "positive_count" to positiveCount.toDouble(),
            "negative_count" to negativeCount.toDouble(),
            "nudge_count" to nudgeCount.toDouble(),
            "final_p_drift_mean" to finalPDriftMean
        )
    }
}
