package com.nophubbing.presenceai.signals

import android.content.Context
import android.content.SharedPreferences

/**
 * UnlockTracker — rolling 10-min unlock count.
 */
class UnlockTracker(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("unlock_tracker", Context.MODE_PRIVATE)

    fun recordUnlock() {
        val now = System.currentTimeMillis()
        val history = getUnlockHistory().toMutableList()
        history.add(now)
        
        // Keep only last 10 minutes
        val tenMinAgo = now - 10 * 60 * 1000
        val filtered = history.filter { it >= tenMinAgo }
        
        saveUnlockHistory(filtered)
    }

    fun getUnlocksLast10Min(): Int {
        val now = System.currentTimeMillis()
        val tenMinAgo = now - 10 * 60 * 1000
        return getUnlockHistory().count { it >= tenMinAgo }
    }

    private fun getUnlockHistory(): List<Long> {
        val raw = prefs.getString("history", "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split(",").mapNotNull { it.toLongOrNull() }
    }

    private fun saveUnlockHistory(history: List<Long>) {
        prefs.edit().putString("history", history.joinToString(",")).apply()
    }
}
