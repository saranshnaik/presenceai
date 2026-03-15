package com.nophubbing.presenceai.signals

import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.*

/**
 * SessionTracker — UsageStatsManager wrapper to detect micro-sessions.
 */
class SessionTracker(private val context: Context) {

    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    fun getMicroSessionStats(windowMs: Long): Pair<Int, Int> {
        val end = System.currentTimeMillis()
        val start = end - windowMs
        
        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
        if (stats.isNullOrEmpty()) return Pair(0, 0)

        var totalSessions = 0
        var microSessions = 0

        // This is a simplified heuristic
        for (usageStat in stats) {
            if (usageStat.totalTimeInForeground > 0) {
                totalSessions++
                if (usageStat.totalTimeInForeground < 30_000) {
                    microSessions++
                }
            }
        }

        return Pair(totalSessions, microSessions)
    }
}
