package com.nophubbing.presenceai.signals

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Tracks the raw 10-minute rolling unlock count (x1).
 * Used by FeatureEngineering to compute x1_unlock_freq.
 */
object UnlockTracker : BroadcastReceiver() {
    private val unlockTimestamps = mutableListOf<Long>()
    
    var lastUnlockTimeMs: Long = 0L
        private set

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_USER_PRESENT) {
            val now = System.currentTimeMillis()
            lastUnlockTimeMs = now
            
            synchronized(unlockTimestamps) {
                unlockTimestamps.add(now)
                // Clean up entries older than 10 minutes
                unlockTimestamps.removeAll { now - it > 10 * 60 * 1000L }
            }
        }
    }

    /**
     * Returns the number of unlocks in the last 10 minutes.
     */
    fun getUnlockCount10Min(): Int {
        val now = System.currentTimeMillis()
        synchronized(unlockTimestamps) {
            unlockTimestamps.removeAll { now - it > 10 * 60 * 1000L }
            return unlockTimestamps.size
        }
    }
}
