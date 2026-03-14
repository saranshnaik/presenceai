package com.nophubbing.presenceai.rl

/**
 * A single screen on/off event emitted by the device's screen-state broadcast receiver.
 *
 * @param timestamp Wall-clock time in milliseconds (System.currentTimeMillis()) at which
 *                  the screen state changed.
 * @param isOn      `true` when the screen turned **on** (ACTION_SCREEN_ON),
 *                  `false` when it turned **off** (ACTION_SCREEN_OFF).
 */
data class ScreenEvent(
    val timestamp: Long,
    val isOn: Boolean
)
