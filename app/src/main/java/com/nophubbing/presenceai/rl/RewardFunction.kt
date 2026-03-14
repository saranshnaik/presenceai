package com.nophubbing.presenceai.rl

import com.nophubbing.presenceai.ml.PipelineConfig

/**
 * RewardFunction.kt — Maps post-nudge observation into a bandit reward float.
 * Pure. No IO.
 *
 * Reward mapping:
 *  +1.0  phone down ≥ BANDIT_FULL_REWARD_S, no re-unlock within RE_UNLOCK_WINDOW_MS
 *  +0.5  phone down BANDIT_PARTIAL_REWARD_S–44s
 *   0.0  ambiguous → Bandit.update() will skip (same state reference returned)
 *  -0.5  notif dismissed < DISMISS_THRESHOLD_MS, no re-unlock
 *  -1.0  notif dismissed < DISMISS_THRESHOLD_MS AND re-unlock < RE_UNLOCK_WINDOW_MS
 */
object RewardFunction {

    fun computeReward(
        phoneDownDurationMs: Long,
        notifDismissedInMs: Long?,    // null if format was HAPTIC or notif not dismissed quickly
        reUnlockWithinWindowMs: Boolean
    ): Float {
        val phoneDownS = phoneDownDurationMs / 1000L

        // Quick dismiss with re-unlock → strong negative
        if (notifDismissedInMs != null &&
            notifDismissedInMs < PipelineConfig.DISMISS_THRESHOLD_MS &&
            reUnlockWithinWindowMs) {
            return -1.0f
        }

        // Quick dismiss, no re-unlock → mild negative
        if (notifDismissedInMs != null &&
            notifDismissedInMs < PipelineConfig.DISMISS_THRESHOLD_MS) {
            return -0.5f
        }

        // Phone stayed down long enough → full reward
        if (phoneDownS >= PipelineConfig.BANDIT_FULL_REWARD_S && !reUnlockWithinWindowMs) {
            return +1.0f
        }

        // Partial compliance
        if (phoneDownS >= PipelineConfig.BANDIT_PARTIAL_REWARD_S) {
            return +0.5f
        }

        // Ambiguous — Bandit.update() will ignore this
        return 0.0f
    }
}
