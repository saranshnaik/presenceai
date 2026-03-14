package com.nophubbing.presenceai.signals

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/**
 * Computes the ratio of micro-sessions (<30s) to total sessions over a time window (x2).
 */
object SessionTracker {

    /**
     * Calculates the micro-session ratio.
     * @param context Application context
     * @param windowMinutes The rolling window in minutes (default 30)
     * @return Ratio of sessions <30s over total sessions [0.0, 1.0]. Returns 0.0 if no sessions.
     */
    fun getMicroSessionRatio(context: Context, windowMinutes: Int = 30): Float {
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val endTime = System.currentTimeMillis()
        val startTime = endTime - (windowMinutes * 60 * 1000L)

        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()

        var totalSessions = 0
        var microSessions = 0
        var lastForegroundTime = 0L

        while (events.hasNextEvent()) {
            events.getNextEvent(event)

            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastForegroundTime = event.timeStamp
            } else if (event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND ||
                event.eventType == UsageEvents.Event.ACTIVITY_STOPPED
            ) {
                if (lastForegroundTime > 0) {
                    val sessionLength = event.timeStamp - lastForegroundTime
                    // A valid session duration
                    if (sessionLength > 0) {
                        totalSessions++
                        if (sessionLength < 30_000L) { // < 30 seconds
                            microSessions++
                        }
                    }
                    // Reset to avoid double counting
                    lastForegroundTime = 0L
                }
            }
        }

        return if (totalSessions == 0) 0.0f else microSessions.toFloat() / totalSessions.toFloat()
    }
}
