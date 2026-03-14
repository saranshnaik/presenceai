package com.nophubbing.presenceai.rl

/**
 * Centralised configuration constants for the ε-Greedy nudge-format bandit.
 *
 * All values are intentionally immutable so every other file in this package can
 * import them without worrying about accidental mutation.
 */
object BanditConfig {

    /**
     * Exploration rate.  20 % of non-forced decisions will pick the less-tried arm
     * instead of the arm with the highest average reward.
     */
    const val EPSILON: Float = 0.20f

    /**
     * Minimum number of completed trials required on each arm before the bandit is
     * allowed to exploit.  Until both arms have reached this threshold every decision
     * is labelled "forced_exploration".
     */
    const val MIN_TRIALS_PER_ARM: Int = 5

    /**
     * Length of the post-nudge observation window in seconds.  The [PostNudgeObserver]
     * collects screen events for this many seconds after the nudge fires.
     */
    const val OBSERVATION_WINDOW_S: Int = 45

    /**
     * Minimum seconds the screen must be off (continuously or cumulatively) within the
     * observation window to qualify for a full +1.0 reward.
     */
    const val BANDIT_FULL_REWARD_S: Int = 45

    /**
     * Minimum seconds the screen must be off to qualify for a partial +0.5 reward.
     * Values below this threshold are ambiguous (0.0) or negative.
     */
    const val BANDIT_PARTIAL_S: Int = 20

    /**
     * Milliseconds after a nudge fires during which a subsequent phone unlock is
     * classified as a "re-unlock" — a strong negative signal.
     */
    const val RE_UNLOCK_WINDOW_MS: Long = 60_000L

    /**
     * Milliseconds within which a notification dismissal is considered reflexive
     * (i.e., the user glanced and immediately dismissed without engaging).
     */
    const val DISMISS_THRESHOLD_MS: Long = 3_000L
}
