package com.nophubbing.presenceai.rl

/**
 * Pure, stateless ε-Greedy bandit logic.
 *
 * This object has **no side effects**: it never touches SharedPreferences, coroutines,
 * or the Android framework.  All state transitions go through [update] which returns a
 * brand-new [BanditState] leaving the original untouched.
 */
object Bandit {

    /**
     * Selects which nudge format to deliver next.
     *
     * Decision priority (evaluated top-to-bottom, first match wins):
     *
     * 1. **Forced exploration** – if either arm has fewer than [BanditConfig.MIN_TRIALS_PER_ARM]
     *    completed trials the bandit must sample the arm with fewer trials.  When both arms are
     *    equally under-sampled, HAPTIC is preferred (same tie-breaking rule as exploitation).
     *
     * 2. **Explore** – if [randomFloat] < [BanditConfig.EPSILON] the bandit picks the arm with
     *    the *fewer* trials (to level out sampling). When trial counts are equal, HAPTIC is
     *    chosen.
     *
     * 3. **Exploit** – pick the arm with the highest average reward.  HAPTIC wins ties.
     *
     * @param state       Current accumulated bandit state.
     * @param randomFloat A value in [0, 1) supplied by the caller (allows deterministic tests
     *                    without mocking Random).
     * @return A [BanditAction] describing the selected format and the reason for the selection.
     */
    fun selectAction(state: BanditState, randomFloat: Float): BanditAction {
        // ── 1. Forced exploration ─────────────────────────────────────────────
        val hapticUnder = state.hapticCount < BanditConfig.MIN_TRIALS_PER_ARM
        val notifUnder  = state.notifCount  < BanditConfig.MIN_TRIALS_PER_ARM

        if (hapticUnder || notifUnder) {
            // Pick the arm we've tried less; HAPTIC wins ties
            val format = when {
                hapticUnder && !notifUnder              -> NudgeFormat.HAPTIC
                !hapticUnder && notifUnder              -> NudgeFormat.NOTIFICATION
                state.hapticCount <= state.notifCount   -> NudgeFormat.HAPTIC   // both under; tie → HAPTIC
                else                                    -> NudgeFormat.NOTIFICATION
            }
            return BanditAction(
                format    = format,
                mode      = "forced_exploration",
                hapticAvg = state.hapticAvg,
                notifAvg  = state.notifAvg
            )
        }

        // ── 2. ε-Greedy exploration ───────────────────────────────────────────
        if (randomFloat < BanditConfig.EPSILON) {
            // Pick the less-tried arm to keep sampling balanced; HAPTIC wins ties
            val format = if (state.notifCount < state.hapticCount) {
                NudgeFormat.NOTIFICATION
            } else {
                NudgeFormat.HAPTIC  // equal counts → HAPTIC
            }
            return BanditAction(
                format    = format,
                mode      = "explore",
                hapticAvg = state.hapticAvg,
                notifAvg  = state.notifAvg
            )
        }

        // ── 3. Exploit ────────────────────────────────────────────────────────
        // Pick arm with highest average reward; HAPTIC wins ties
        val format = if (state.notifAvg > state.hapticAvg) {
            NudgeFormat.NOTIFICATION
        } else {
            NudgeFormat.HAPTIC
        }
        return BanditAction(
            format    = format,
            mode      = "exploit",
            hapticAvg = state.hapticAvg,
            notifAvg  = state.notifAvg
        )
    }

    /**
     * Incorporates an observed reward into the bandit state, returning a new [BanditState].
     *
     * This function is **pure and immutable**: the original [state] is never modified.
     *
     * @param state   The state prior to this update.
     * @param format  Which arm the nudge was delivered on.
     * @param reward  The observed reward.  Must be one of {-1f, -0.5f, 0.5f, 1f}.
     *                - If [reward] is exactly `0f` (ambiguous outcome) the same [state]
     *                  reference is returned unchanged — no trial is recorded.
     *                - Any value outside the valid set is treated the same as `0f` and
     *                  discarded (no trial recorded, same reference returned).
     * @return Updated [BanditState], or the original [state] when [reward] is 0f / invalid.
     */
    fun update(state: BanditState, format: NudgeFormat, reward: Float): BanditState {
        // Validate reward — only these four values count as completed trials
        val validRewards = setOf(-1f, -0.5f, 0.5f, 1f)
        if (reward !in validRewards) return state   // 0f or garbage → discard

        return when (format) {
            NudgeFormat.HAPTIC -> state.copy(
                hapticCount       = state.hapticCount + 1,
                hapticTotalReward = state.hapticTotalReward + reward
            )
            NudgeFormat.NOTIFICATION -> state.copy(
                notifCount       = state.notifCount + 1,
                notifTotalReward = state.notifTotalReward + reward
            )
        }
    }
}
