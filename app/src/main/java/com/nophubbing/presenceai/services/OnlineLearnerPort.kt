package com.nophubbing.presenceai.services

/**
 * OnlineLearnerPort — decouples FeedbackActivityMonitor from PipelineRunner.
 *
 * update() is called after the 20s observation window with the merged label.
 * rewardCallback is called with the bandit reward so the ViewModel can
 * update BanditState from button-tap feedback (not just PostNudgeObserver).
 */
interface OnlineLearnerPort {
    /** Apply one LR gradient step. label: 1 = phubbing, 0 = attentive. */
    fun update(features: FloatArray, label: Int)
}
