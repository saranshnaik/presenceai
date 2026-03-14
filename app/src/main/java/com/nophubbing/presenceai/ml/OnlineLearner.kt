package com.nophubbing.presenceai.ml

import java.util.Date
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * OnlineLearner.kt — online gradient descent + metric computation.
 * Pure object. No IO.
 *
 * One update step:
 *   error    = label - P(drift)
 *   w_new[i] = w_old[i] + lr × error × features[i]
 *   bias_new = bias_old + lr × error
 *
 * label == -1.0 → return SAME reference (live inference, no label yet)
 * label ∈ {0.0, 1.0} → return NEW LRWeights object (immutable update)
 */
object OnlineLearner {

    fun computeLoss(pDrift: Double, label: Double): Double {
        if (label != 0.0 && label != 1.0) return 0.0
        val p = pDrift.coerceIn(1e-7, 1.0 - 1e-7)
        return -(label * ln(p) + (1.0 - label) * ln(1.0 - p))
    }

    fun computeAccuracy(pDrift: Double, label: Double, threshold: Double = 0.5): Double {
        if (label != 0.0 && label != 1.0) return 0.0
        val predicted = if (pDrift > threshold) 1.0 else 0.0
        return if (predicted == label) 1.0 else 0.0
    }

    /**
     * Returns a NEW immutable LRWeights object (or SAME reference if label == -1.0).
     */
    fun update(
        features: List<Double>,
        label: Double,
        weights: LRWeights,
        config: PipelineConfig
    ): LRWeights {
        if (label == -1.0) return weights  // ambiguous — same reference, no copy

        require(label == 0.0 || label == 1.0) {
            "Invalid label: $label. Must be 0.0, 1.0, or -1.0"
        }

        val pDrift = LrClassifier.predict(features, weights)
        val error  = label - pDrift
        val lr     = config.lr_learning_rate

        val newW = weights.asList().mapIndexed { i, w -> w + lr * error * features[i] }

        return LRWeights(
            w            = newW,
            bias         = weights.bias + lr * error,
            update_count = weights.update_count + 1,
            last_updated = Date().toString()
        )
    }

    fun trainOnBatch(
        rows: List<SignalRow>,
        initialWeights: LRWeights,
        config: PipelineConfig
    ): Pair<LRWeights, List<TrainingStep>> {
        val steps = mutableListOf<TrainingStep>()
        var currentWeights = initialWeights

        for ((i, row) in rows.withIndex()) {
            val fv     = FeatureEngineering.buildFeatureVector(row).asList()
            val pDrift = LrClassifier.predict(fv, currentWeights)
            val pPhub  = LrClassifier.computePPhub(fv, currentWeights, config)
            val nudge  = LrClassifier.shouldNudge(fv, currentWeights, config)

            val error: Double?
            if (row.label == -1.0) {
                error = null
            } else {
                error = row.label - pDrift
                currentWeights = update(fv, row.label, currentWeights, config)
            }

            steps.add(TrainingStep(
                step             = i,
                p_drift          = pDrift,
                p_phub           = pPhub,
                should_nudge     = nudge,
                label            = row.label,
                error            = error,
                weights_snapshot = currentWeights.asList()
            ))
        }
        return Pair(currentWeights, steps)
    }

    fun computeEpochMetrics(steps: List<TrainingStep>): Map<String, Double> {
        val total   = steps.size
        val labeled = steps.filter { it.label != -1.0 }
        val nudges  = steps.count { it.should_nudge }

        val avgLoss = if (labeled.isEmpty()) 0.0
                      else labeled.sumOf { computeLoss(it.p_drift, it.label) } / labeled.size
        val accuracy = if (labeled.isEmpty()) 0.0
                       else labeled.sumOf { computeAccuracy(it.p_drift, it.label) } / labeled.size

        return mapOf(
            "total_steps"      to total.toDouble(),
            "labeled_steps"    to labeled.size.toDouble(),
            "discarded_steps"  to (total - labeled.size).toDouble(),
            "avg_loss"         to avgLoss,
            "accuracy"         to accuracy,
            "positive_count"   to steps.count { it.label == 1.0 }.toDouble(),
            "negative_count"   to steps.count { it.label == 0.0 }.toDouble(),
            "nudge_count"      to nudges.toDouble(),
            "final_p_drift_mean" to if (total > 0) steps.sumOf { it.p_drift } / total else 0.0
        )
    }
}
