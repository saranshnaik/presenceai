package com.nophubbing.presenceai.signals

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

/**
 * ScreenMonitor — BroadcastReceiver for ACTION_SCREEN_ON/OFF.
 */
class ScreenMonitor(
    private val context: Context,
    private val onScreenEvent: (Boolean) -> Unit
) {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> onScreenEvent(true)
                Intent.ACTION_SCREEN_OFF -> onScreenEvent(false)
            }
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        context.registerReceiver(receiver, filter)
    }

    fun stop() {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) {}
    }
}
