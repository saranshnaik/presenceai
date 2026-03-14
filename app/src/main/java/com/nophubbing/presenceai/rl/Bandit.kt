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

package com.nophubbing.presenceai.rl

import com.nophubbing.presenceai.ml.PipelineConfig

enum class NudgeFormat { HAPTIC, NOTIFICATION }

/**
 * BanditState — 4 counters in an immutable value object.
 * Persisted by BanditStore (SharedPreferences). Never mutated in-place.
 */
data class BanditState(
    val hapticCount: Int = 0,
    val hapticTotalReward: Float = 0f,
    val notifCount: Int = 0,
    val notifTotalReward: Float = 0f
) {
    val hapticAvg: Float get() = if (hapticCount > 0) hapticTotalReward / hapticCount else 0f
    val notifAvg: Float  get() = if (notifCount > 0) notifTotalReward / notifCount else 0f
}

/**
 * Bandit.kt — ε-Greedy 2-arm bandit. Pure. No IO. No Room. No SharedPreferences.
 *
 * Decision order (strict):
 *  1. hapticCount < MIN_TRIALS → force HAPTIC
 *  2. notifCount  < MIN_TRIALS → force NOTIFICATION
 *  3. random < EPSILON → explore: pick lower-count arm
 *  4. else → exploit: pick higher avg reward arm
 *  Tie-break → HAPTIC (less intrusive when uncertain)
 *
 * Reward mapping:
 *   +1.0  phone down ≥ 45s, no re-unlock within 60s
 *   +0.5  phone down 20–44s
 *    0.0  ambiguous → skip update (return same state reference)
 *   -0.5  notif dismissed < 3s, no re-unlock
 *   -1.0  notif dismissed < 3s AND re-unlock < 60s
 */
object Bandit {

    private val random = java.util.Random()

    fun selectAction(state: BanditState): NudgeFormat {
        val min = PipelineConfig.MIN_TRIALS_PER_ARM
        return when {
            state.hapticCount < min -> NudgeFormat.HAPTIC
            state.notifCount < min  -> NudgeFormat.NOTIFICATION
            random.nextFloat() < PipelineConfig.EPSILON -> {
                // Explore: pick arm with fewer trials
                if (state.hapticCount <= state.notifCount) NudgeFormat.HAPTIC else NudgeFormat.NOTIFICATION
            }
            else -> {
                // Exploit: pick arm with higher avg reward; tie → HAPTIC
                if (state.notifAvg > state.hapticAvg) NudgeFormat.NOTIFICATION else NudgeFormat.HAPTIC
            }
        }
    }

    /**
     * Returns updated state or SAME reference if reward == 0.0 (ambiguous).
     */
    fun update(state: BanditState, format: NudgeFormat, reward: Float): BanditState {
        if (reward == 0.0f) return state  // ambiguous — same reference
        return when (format) {
            NudgeFormat.HAPTIC -> state.copy(
                hapticCount = state.hapticCount + 1,
                hapticTotalReward = state.hapticTotalReward + reward
            )
            NudgeFormat.NOTIFICATION -> state.copy(
                notifCount = state.notifCount + 1,
                notifTotalReward = state.notifTotalReward + reward
            )
        }
    }

    fun getStats(state: BanditState): Map<String, Any> = mapOf(
        "haptic_count"   to state.hapticCount,
        "haptic_avg"     to state.hapticAvg,
        "notif_count"    to state.notifCount,
        "notif_avg"      to state.notifAvg,
        "preferred_arm"  to if (state.hapticAvg >= state.notifAvg) "HAPTIC" else "NOTIFICATION"
    )
}
