package com.nophubbing.presenceai.rl

/**
 * The two delivery formats the bandit chooses between when the ML layer decides to send a nudge.
 *
 * - [HAPTIC]       – A silent vibration pattern.  No visual chrome, no notification shade.
 *                   Because it produces no dismissible UI, it can never trigger dismiss penalties
 *                   in [RewardFunction].
 * - [NOTIFICATION] – A system notification.  Visible in the shade and on the lock screen.
 *                   Can be dismissed reflexively, which is modelled as a negative reward signal.
 */
enum class NudgeFormat {
    HAPTIC,
    NOTIFICATION
}
