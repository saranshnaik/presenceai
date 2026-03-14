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
