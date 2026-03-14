package com.nophubbing.presenceai.rl

import kotlin.math.abs

/**
 * Immutable accumulator for the bandit's per-arm trial and reward history.
 *
 * All mutation returns a **new** instance — the state is never mutated in place.
 * This makes it trivially safe to share across coroutines and straightforward to test.
 *
 * @param hapticCount       Total completed trials for the [NudgeFormat.HAPTIC] arm.
 * @param hapticTotalReward Cumulative reward collected by the HAPTIC arm (sum of individual
 *                          rewards, which lie in {-1f, -0.5f, 0.5f, 1f}).
 * @param notifCount        Total completed trials for the [NudgeFormat.NOTIFICATION] arm.
 * @param notifTotalReward  Cumulative reward collected by the NOTIFICATION arm.
 */
data class BanditState(
    val hapticCount: Int = 0,
    val hapticTotalReward: Float = 0f,
    val notifCount: Int = 0,
    val notifTotalReward: Float = 0f
) {
    // ── Computed Properties ───────────────────────────────────────────────────

    /**
     * Average reward for the HAPTIC arm.
     * Returns `0f` when no trials have been completed (avoids division by zero).
     */
    val hapticAvg: Float
        get() = if (hapticCount == 0) 0f else hapticTotalReward / hapticCount

    /**
     * Average reward for the NOTIFICATION arm.
     * Returns `0f` when no trials have been completed (avoids division by zero).
     */
    val notifAvg: Float
        get() = if (notifCount == 0) 0f else notifTotalReward / notifCount

    /**
     * Total trials completed across both arms.
     */
    val totalTrials: Int
        get() = hapticCount + notifCount

    /**
     * The arm that currently has the highest average reward, or `null` if either
     * arm has fewer than [BanditConfig.MIN_TRIALS_PER_ARM] trials and the bandit
     * should still be in forced-exploration mode.
     *
     * Tie-breaking rule: HAPTIC wins ties (consistent with [Bandit.selectAction]).
     */
    val preferredArm: NudgeFormat?
        get() {
            if (hapticCount < BanditConfig.MIN_TRIALS_PER_ARM ||
                notifCount  < BanditConfig.MIN_TRIALS_PER_ARM) return null
            return if (notifAvg > hapticAvg) NudgeFormat.NOTIFICATION else NudgeFormat.HAPTIC
        }

    /**
     * Absolute difference between the two arm averages.
     *
     * A high confidence means the bandit has a strong preference for one arm.
     * Approaches 0 when both arms perform similarly.
     */
    val confidence: Float
        get() = abs(hapticAvg - notifAvg)
}
