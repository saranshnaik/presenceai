package com.nophubbing.presenceai.rl

import com.nophubbing.presenceai.ml.PipelineConfig

data class ResolvedLabels(
    val lrLabel: Double,       // 1.0, 0.0, or -1.0 (ambiguous)
    val banditReward: Float,   // from RewardFunction
    val format: NudgeFormat
)

/**
 * LabelResolver.kt — resolveAll() is the ONLY function that produces labels
 * for both models. One 45s window → one call → both lrLabel AND banditReward.
 *
 * NEVER call resolveLrLabel() and computeReward() separately in production.
 * This is Coupling Point 1 in the architecture.
 */
object LabelResolver {

    fun resolveAll(
        phoneDownDurationMs: Long,
        notifDismissedInMs: Long?,
        reUnlockWithinWindowMs: Boolean,
        format: NudgeFormat
    ): ResolvedLabels {
        val banditReward = RewardFunction.computeReward(
            phoneDownDurationMs, notifDismissedInMs, reUnlockWithinWindowMs
        )

        val phoneDownS = phoneDownDurationMs / 1000L
        val lrLabel = when {
            phoneDownS >= PipelineConfig.POSITIVE_LABEL_THRESHOLD_S && !reUnlockWithinWindowMs -> 1.0
            phoneDownS < PipelineConfig.BANDIT_PARTIAL_REWARD_S || reUnlockWithinWindowMs      -> 0.0
            else                                                                                 -> -1.0
        }

        return ResolvedLabels(lrLabel, banditReward, format)
    }
}
