package com.nophubbing.presenceai.rl

/**
 * Observation bundle passed to [RewardFunction.computeReward].
 *
 * Assembled by [PostNudgeObserver] after the 45-second window closes.
 *
 * @param format            The nudge format that was delivered.
 * @param nudgeTimeMs       Wall-clock timestamp (ms) when the nudge fired.
 * @param screenEvents      All [ScreenEvent]s recorded during the observation window.
 * @param wasDismissed      True if the user dismissed a NOTIFICATION-format nudge.
 * @param dismissTimeMs     Wall-clock timestamp (ms) of the dismissal action,
 *                          or `null` if the nudge was not dismissed.
 * @param reUnlockTimeMs    Wall-clock timestamp (ms) of the first unlock that occurred
 *                          within [BanditConfig.RE_UNLOCK_WINDOW_MS] after the nudge,
 *                          or `null` if no such unlock occurred.
 */
data class NudgeObservation(
    val format: NudgeFormat,
    val nudgeTimeMs: Long,
    val screenEvents: List<ScreenEvent>,
    val wasDismissed: Boolean,
    val dismissTimeMs: Long?,
    val reUnlockTimeMs: Long?
)

/**
 * Pure, stateless reward calculator.
 *
 * Converts a [NudgeObservation] into a scalar reward in {-1f, -0.5f, 0f, 0.5f, 1f}.
 * Zero means "ambiguous — discard this trial" (see [Bandit.update]).
 *
 * No Android framework dependencies, no coroutines, fully testable in plain JUnit.
 */
object RewardFunction {

    /**
     * Calculates how many seconds the phone screen was **off** within the
     * [BanditConfig.OBSERVATION_WINDOW_S]-second window following [nudgeTimeMs].
     *
     * Algorithm:
     * - Walk through [events] chronologically.
     * - Track the start of each "screen-off" interval.
     * - Clip each interval to the [nudgeTimeMs, windowEndMs] boundary.
     * - Sum the clipped durations and convert to seconds.
     *
     * Edge cases:
     * - If the screen was already off when the nudge fired, the off-interval starts
     *   at [nudgeTimeMs].
     * - Events outside the observation window are ignored.
     *
     * @param events      Screen events sorted by [ScreenEvent.timestamp] ascending (need not
     *                    be pre-sorted — the function sorts internally for safety).
     * @param nudgeTimeMs Absolute start of the observation window (ms).
     * @return Cumulative screen-off time in **seconds** (can be 0 if screen never turned off).
     */
    fun computePhoneDownSeconds(events: List<ScreenEvent>, nudgeTimeMs: Long): Int {
        val windowEndMs = nudgeTimeMs + BanditConfig.OBSERVATION_WINDOW_S * 1_000L
        val sorted = events.sortedBy { it.timestamp }

        var totalOffMs = 0L
        var offStart: Long? = null

        // Determine screen state at window start by looking at the last event before nudgeTimeMs
        val lastBeforeNudge = sorted.lastOrNull { it.timestamp <= nudgeTimeMs }
        val screenOnAtStart = lastBeforeNudge?.isOn ?: true  // assume on if no prior event

        if (!screenOnAtStart) {
            offStart = nudgeTimeMs  // screen was already off when nudge fired
        }

        // Walk events within the observation window
        for (event in sorted) {
            val t = event.timestamp
            if (t <= nudgeTimeMs) continue   // before window
            if (t >= windowEndMs) break      // after window

            when {
                !event.isOn && offStart == null -> {
                    // Screen just turned off — start accumulating
                    offStart = t
                }
                event.isOn && offStart != null -> {
                    // Screen turned back on — close the off-interval
                    totalOffMs += (t - offStart!!)
                    offStart = null
                }
            }
        }

        // If screen was still off at window end, close the final interval
        if (offStart != null) {
            totalOffMs += (windowEndMs - offStart!!)
        }

        return (totalOffMs / 1_000L).toInt()
    }

    /**
     * Maps a [NudgeObservation] to a scalar reward signal.
     *
     * Reward table (evaluated in priority order):
     *
     * | Condition                                                       | Reward |
     * |-----------------------------------------------------------------|--------|
     * | Phone down ≥ 45 s AND no re-unlock within 60 s                | +1.0   |
     * | Phone down 20–44 s (regardless of re-unlock or format)         | +0.5   |
     * | NOTIFICATION dismissed < 3 s AND re-unlock within 60 s         | −1.0   |
     * | NOTIFICATION dismissed < 3 s, no re-unlock within 60 s         | −0.5   |
     * | Everything else (ambiguous)                                     |  0.0   |
     *
     * Note: HAPTIC format can **never** trigger a dismiss penalty because it produces
     * no dismissible UI element.
     *
     * @param obs The assembled observation from [PostNudgeObserver].
     * @return A reward in {-1f, -0.5f, 0f, 0.5f, 1f}.
     */
    fun computeReward(obs: NudgeObservation): Float {
        val phoneDownSeconds = computePhoneDownSeconds(obs.screenEvents, obs.nudgeTimeMs)

        // ── Positive rewards (phone was put down) ─────────────────────────────
        if (phoneDownSeconds >= BanditConfig.BANDIT_FULL_REWARD_S) {
            val hasReUnlock = obs.reUnlockTimeMs != null &&
                    (obs.reUnlockTimeMs - obs.nudgeTimeMs) < BanditConfig.RE_UNLOCK_WINDOW_MS
            if (!hasReUnlock) return 1f
        }

        if (phoneDownSeconds in BanditConfig.BANDIT_PARTIAL_S until BanditConfig.BANDIT_FULL_REWARD_S) {
            return 0.5f
        }

        // ── Negative rewards (NOTIFICATION only — HAPTIC cannot be dismissed) ─
        if (obs.format == NudgeFormat.NOTIFICATION && obs.wasDismissed) {
            val dismissMs = obs.dismissTimeMs
            if (dismissMs != null &&
                (dismissMs - obs.nudgeTimeMs) < BanditConfig.DISMISS_THRESHOLD_MS) {

                val hasReUnlock = obs.reUnlockTimeMs != null &&
                        (obs.reUnlockTimeMs - obs.nudgeTimeMs) < BanditConfig.RE_UNLOCK_WINDOW_MS

                return if (hasReUnlock) -1f else -0.5f
            }
        }

        // ── Ambiguous / else ──────────────────────────────────────────────────
        return 0f
    }
}
