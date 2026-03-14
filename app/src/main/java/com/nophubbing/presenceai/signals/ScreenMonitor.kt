package com.nophubbing.presenceai.signals

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Tracks the screen on/off state to assist with session tracking and other metrics.
 */
object ScreenMonitor : BroadcastReceiver() {
    var isScreenOn: Boolean = true
        private set
    var lastScreenOnTimeMs: Long = System.currentTimeMillis()
        private set
    var lastScreenOffTimeMs: Long = 0L
        private set

    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SCREEN_ON -> {
                isScreenOn = true
                lastScreenOnTimeMs = System.currentTimeMillis()
            }
            Intent.ACTION_SCREEN_OFF -> {
                isScreenOn = false
                lastScreenOffTimeMs = System.currentTimeMillis()
            }
        }
    }
}
