package com.nophubbing.presenceai.rl

/**
 * Describes a single arm-selection decision made by [Bandit.selectAction].
 *
 * Returned to the caller so the delivery layer knows what to show **and** the
 * analytics/logging layer knows why the bandit chose it.
 *
 * @param format    The nudge format selected for this trial ([NudgeFormat.HAPTIC] or
 *                  [NudgeFormat.NOTIFICATION]).
 * @param mode      Human-readable reason for the selection:
 *                  - `"forced_exploration"` – fewer than [BanditConfig.MIN_TRIALS_PER_ARM]
 *                    trials completed on at least one arm; the bandit must sample both arms
 *                    before it can exploit.
 *                  - `"explore"`            – random exploration tick (ε-greedy random branch).
 *                  - `"exploit"`            – greedy selection of the arm with the highest
 *                    average reward.
 * @param hapticAvg Snapshot of the HAPTIC arm's average reward at decision time.
 * @param notifAvg  Snapshot of the NOTIFICATION arm's average reward at decision time.
 */
data class BanditAction(
    val format: NudgeFormat,
    val mode: String,
    val hapticAvg: Float,
    val notifAvg: Float
)
