package com.nophubbing.presenceai.ml

import java.io.File

class PipelineRunner(private val config: PipelineConfig) {
    var weights = LRWeights.defaults()
    val steps = mutableListOf<TrainingStep>()
    var labeledCount = 0
    var step = 0

    fun processRow(row: SignalRow): TrainingStep {
        // 1. Extract
        val fv = FeatureEngineering.buildFeatureVector(row)
        val fvList = fv.asList()

        // 2-4. Inference BEFORE learning
        val pDrift = LrClassifier.predict(fvList, weights)
        val pPhub = LrClassifier.computePPhub(fvList, weights, config)
        val nudge = LrClassifier.shouldNudge(fvList, weights, config)

        // 7. Learning (only if not discarded)
        var error: Double? = null
        if (row.label != -1.0) {
            error = row.label - pDrift
            weights = OnlineLearner.update(fvList, row.label, weights, config)
            labeledCount++
        }

        // 8. Record step
        val stepRecord = TrainingStep(
            step = this.step,
            p_drift = pDrift,
            p_phub = pPhub,
            should_nudge = nudge,
            label = row.label,
            error = error,
            weights_snapshot = weights.asList()
        )
        
        return stepRecord
    }

    fun run(rows: List<SignalRow>): Map<String, Any> {
        for (row in rows) {
            val stepRecord = processRow(row)
            steps.add(stepRecord)
            step++
        }

        val metrics = OnlineLearner.computeEpochMetrics(steps)

        return mapOf(
            "total_rows" to step,
            "labeled_rows" to labeledCount,
            "discarded_rows" to (step - labeledCount),
            "lr_update_count" to weights.update_count,
            "final_weights" to weights,
            "avg_loss" to (metrics["avg_loss"] ?: 0.0),
            "accuracy" to (metrics["accuracy"] ?: 0.0),
            "nudge_count" to (metrics["nudge_count"] ?: 0.0)
        )
    }
}
