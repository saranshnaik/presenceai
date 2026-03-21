package com.nophubbing.presenceai.genai

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * InsightCache — SharedPreferences-backed 7-day TTL cache for weekly insights.
 *
 * This is the ONLY file that reads/writes SharedPreferences for insight caching.
 * Prevents spamming the Claude API on every screen open.
 */
class InsightCache(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("insight_cache", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "InsightCache"
        private const val KEY_WEEKLY_TEXT     = "weekly_insight_text"
        private const val KEY_WEEKLY_EXPIRES  = "weekly_insight_expires"
        private const val TTL_MS              = 7 * 24 * 60 * 60 * 1_000L  // 7 days
    }

    /** Returns cached weekly insight or null if expired / missing. */
    fun getWeeklyInsight(): String? {
        return try {
            val expires = prefs.getLong(KEY_WEEKLY_EXPIRES, 0L)
            if (System.currentTimeMillis() > expires) {
                null
            } else {
                prefs.getString(KEY_WEEKLY_TEXT, null)?.takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "getWeeklyInsight failed: ${e.message}")
            null
        }
    }

    /** Stores a weekly insight with a 7-day expiry. */
    fun putWeeklyInsight(text: String) {
        try {
            prefs.edit()
                .putString(KEY_WEEKLY_TEXT, text)
                .putLong(KEY_WEEKLY_EXPIRES, System.currentTimeMillis() + TTL_MS)
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "putWeeklyInsight failed: ${e.message}")
        }
    }

    /** Invalidates the cache, forcing regeneration on next request. */
    fun invalidate() {
        prefs.edit().remove(KEY_WEEKLY_EXPIRES).apply()
    }
}
