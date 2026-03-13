package com.nophubbing.presenceai.services

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UsageEventsCollector(private val context: Context) {

    fun printRecentForegroundEvents(windowMinutes: Int = 30) {

        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val endTime = System.currentTimeMillis()
        val startTime = endTime - (windowMinutes * 60 * 1000L)

        val events: UsageEvents =
            usageStatsManager.queryEvents(startTime, endTime)

        val event = UsageEvents.Event()

        val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        while (events.hasNextEvent()) {

            events.getNextEvent(event)

            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {

                val time = formatter.format(Date(event.timeStamp))

                Log.d(
                    "PresenceAI",
                    "Foreground App: ${event.packageName} at $time"
                )
            }
        }
    }
}