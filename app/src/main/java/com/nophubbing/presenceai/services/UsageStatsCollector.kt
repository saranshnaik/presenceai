package com.nophubbing.presenceai.services

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log

class UsageStatsCollector(private val context: Context) {

    fun getRecentUsageStats(): List<UsageStats> {

        Log.d("PresenceAI", "Collector started")

        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val endTime = System.currentTimeMillis()
        val startTime = endTime - (1000 * 60 * 60)

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        Log.d("PresenceAI", "Stats size: ${stats?.size}")

        return stats ?: emptyList()
    }

    fun printUsageStats() {

        val stats = getRecentUsageStats()

        for (usage in stats) {

            if (usage.totalTimeInForeground > 0) {

                Log.d(
                    "PresenceAI",
                    "App: ${usage.packageName} | Foreground(ms): ${usage.totalTimeInForeground}"
                )
            }
        }
    }
}