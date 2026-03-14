package com.nophubbing.presenceai.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

object UnlockCounter {
    private val unlockTimestamps = mutableListOf<Long>()

    @Synchronized
    fun increment() {
        unlockTimestamps.add(System.currentTimeMillis())
    }

    @Synchronized
    fun getCount(windowMinutes: Int): Int {
        val cutoff = System.currentTimeMillis() - (windowMinutes * 60 * 1000L)
        unlockTimestamps.removeAll { it < cutoff }
        return unlockTimestamps.size
    }
}

class UnlockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_USER_PRESENT) {
            UnlockCounter.increment()
            Log.d("PresenceAI", "Unlock detected. Current window count: ${UnlockCounter.getCount(10)}")
        }
    }
}