package com.nophubbing.presenceai.ml

/**
 * PipelineRunner.kt — stateful runner that chains inference + online learning.
 * Holds current weights and a step history in memory.
 *
 * Inference order (strict — never change):
 *   1. buildFeatureVector(row)
 *   2. predict()  → P(drift)
 *   3. computePPhub() → P(phub)
 *   4. shouldNudge()
 *   5. IF label != -1.0 → updateWeights() (online learning)
 *   6. record TrainingStep
 */
class PipelineRunner {

    var weights: LRWeights = LRWeights.defaults()

    val steps = mutableListOf<TrainingStep>()
    var labeledCount = 0
        private set
    var step = 0
        private set

    /** Process a single row — inference first, then optional weight update. */
    fun processRow(row: SignalRow): TrainingStep {
        val fv     = FeatureEngineering.buildFeatureVector(row)
        val fvList = fv.map { it.toDouble() }
        
        val pDrift = LrClassifier.predict(fvList, weights)
        val pPhub  = LrClassifier.computePPhub(fvList, weights, PipelineConfig)
        val nudge  = LrClassifier.shouldNudge(fvList, weights, PipelineConfig)

        var error: Double? = null
        if (row.label != -1.0) {
            error = row.label - pDrift
            labeledCount++
        }

        val record = TrainingStep(
            step             = step,
            p_drift          = pDrift,
            p_phub           = pPhub,
            should_nudge     = nudge,
            label            = row.label,
            error            = error,
            weights_snapshot = weights.asList()
        )
        steps.add(record)
        step++
        return record
    }

    /** Process a batch of rows and return aggregate metrics. */
    fun run(rows: List<SignalRow>): Map<String, Any> {
        rows.forEach { processRow(it) }
        val metrics = OnlineLearner.computeEpochMetrics(steps)
        return mapOf(
            "total_rows"     to step,
            "labeled_rows"   to labeledCount,
            "discarded_rows" to (step - labeledCount),
            "lr_update_count" to weights.update_count,
            "final_weights"  to weights,
            "avg_loss"       to (metrics["avg_loss"]   ?: 0.0),
            "accuracy"       to (metrics["accuracy"]   ?: 0.0),
            "nudge_count"    to (metrics["nudge_count"] ?: 0.0)
        )
    }

    /** Current epoch accuracy over labeled steps only. */
    fun currentAccuracy(): Float {
        val labeled = steps.filter { it.label != -1.0 }
        if (labeled.isEmpty()) return 0f
        val correct = labeled.count {
            (it.p_drift > 0.5 && it.label == 1.0) || (it.p_drift <= 0.5 && it.label == 0.0)
        }
        return correct.toFloat() / labeled.size
    }
}
