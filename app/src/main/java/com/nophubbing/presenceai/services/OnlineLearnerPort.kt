package com.nophubbing.presenceai.services

/**
 * OnlineLearnerPort
 *
 * A minimal interface that decouples FeedbackActivityMonitor and
 * FalseNegativeGuard from the concrete OnlineLearner / PipelineRunner.
 *
 * The real implementation is PresenceOnlineLearnerAdapter (inside PipelineRunner.kt),
 * which routes every call back to PipelineRunner.applyExternalWeightUpdate()
 * so all weight mutations stay in one place.
 */
interface OnlineLearnerPort {
    /**
     * Apply a single online learning step.
     *
     * @param features  raw feature vector from FeatureEngineering (same length as weights)
     * @param label     ground-truth label: 1 = phubbing, 0 = attentive
     */
    fun update(features: FloatArray, label: Int)
}
