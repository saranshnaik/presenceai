package com.nophubbing.presenceai.rl

import java.util.Random

/**
 * Bandit.kt — ε-Greedy 2-arm bandit.
 *
 * This object manages the logic for selecting nudge formats and updating rewards.
 * It provides both a simple API for the UI and a more detailed one for tests/logging.
 */
object Bandit {

    private val random = Random()

    /**
     * Simple version of selectAction for the UI.
     * Uses internal Random and returns only the selected NudgeFormat.
     */
    fun selectAction(state: BanditState): NudgeFormat {
        return selectAction(state, random.nextFloat()).format
    }

    /**
     * Full version of selectAction returning BanditAction with metadata.
     * Allows passing randomFloat for deterministic testing.
     */
    fun selectAction(state: BanditState, randomFloat: Float): BanditAction {
        val min = BanditConfig.MIN_TRIALS_PER_ARM
        val hapticUnder = state.hapticCount < min
        val notifUnder  = state.notifCount  < min

        // 1. Forced exploration
        if (hapticUnder || notifUnder) {
            val format = when {
                hapticUnder && !notifUnder              -> NudgeFormat.HAPTIC
                !hapticUnder && notifUnder              -> NudgeFormat.NOTIFICATION
                state.hapticCount <= state.notifCount   -> NudgeFormat.HAPTIC
                else                                    -> NudgeFormat.NOTIFICATION
            }
            return BanditAction(
                format = format,
                mode = "forced_exploration",
                hapticAvg = state.hapticAvg,
                notifAvg = state.notifAvg
            )
        }

        // 2. ε-Greedy exploration
        if (randomFloat < BanditConfig.EPSILON) {
            val format = if (state.notifCount < state.hapticCount) {
                NudgeFormat.NOTIFICATION
            } else {
                NudgeFormat.HAPTIC
            }
            return BanditAction(
                format = format,
                mode = "explore",
                hapticAvg = state.hapticAvg,
                notifAvg = state.notifAvg
            )
        }

        // 3. Exploit
        val format = if (state.notifAvg > state.hapticAvg) {
            NudgeFormat.NOTIFICATION
        } else {
            NudgeFormat.HAPTIC
        }
        return BanditAction(
            format = format,
            mode = "exploit",
            hapticAvg = state.hapticAvg,
            notifAvg = state.notifAvg
        )
    }

    /**
     * Incorporates an observed reward into the bandit state, returning a new [BanditState].
     * Returns the same reference if reward is 0.0f (ambiguous).
     */
    fun update(state: BanditState, format: NudgeFormat, reward: Float): BanditState {
        // We consider -1.0, -0.5, 0.5, 1.0 as valid reward signals.
        // 0.0 is considered ambiguous and does not update the state.
        val validRewards = setOf(-1f, -0.5f, 0.5f, 1f)
        if (reward !in validRewards) return state

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

    /**
     * Returns a map of stats for display/logging.
     */
    fun getStats(state: BanditState): Map<String, Any> = mapOf(
        "haptic_count"   to state.hapticCount,
        "haptic_avg"     to state.hapticAvg,
        "notif_count"    to state.notifCount,
        "notif_avg"      to state.notifAvg,
        "preferred_arm"  to (state.preferredArm?.name ?: "NONE")
    )
}
