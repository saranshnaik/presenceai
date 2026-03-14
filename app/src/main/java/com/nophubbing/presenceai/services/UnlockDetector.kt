package com.nophubbing.presenceai.services

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log

class UnlockDetector(private val context: Context) {

    private var lastCheckTime = System.currentTimeMillis()

    fun getUnlockCount(): Int {

        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val now = System.currentTimeMillis()

        val events = usageStatsManager.queryEvents(lastCheckTime, now)

        val event = UsageEvents.Event()

        var unlocks = 0

        Log.d("PresenceAI", "UnlockDetector.getUnlockCount from $lastCheckTime to $now")

        while (events.hasNextEvent()) {

            events.getNextEvent(event)

            when (event.eventType) {

                UsageEvents.Event.KEYGUARD_HIDDEN -> {

                    unlocks++
                    Log.d("PresenceAI", "DETECTED_UNLOCK_USAGE via KEYGUARD_HIDDEN at ${event.timeStamp}")
                }

                UsageEvents.Event.MOVE_TO_FOREGROUND -> {

                    if (!isSystemPackage(event.packageName)) {

                        // Do NOT treat foreground app starts as unlocks; log only for debugging.
                        Log.d(
                            "PresenceAI",
                            "FOREGROUND_APP_EVENT (not counted as unlock): ${event.packageName} at ${event.timeStamp}"
                        )
                    }
                }
            }
        }

        lastCheckTime = now

        return unlocks
    }

    private fun isSystemPackage(pkg: String): Boolean {

        val ignoredPrefixes = listOf(
            "com.android.",
            "com.miui.",
            "com.google.android.gms",
            "com.nophubbing.presenceai"
        )

        return ignoredPrefixes.any { pkg.startsWith(it) }
    }
}