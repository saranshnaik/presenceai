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

        while (events.hasNextEvent()) {

            events.getNextEvent(event)

            when (event.eventType) {

                UsageEvents.Event.KEYGUARD_HIDDEN -> {

                    unlocks++
                    Log.d("PresenceAI", "Unlock detected via KEYGUARD_HIDDEN")
                }

                UsageEvents.Event.MOVE_TO_FOREGROUND -> {

                    if (!isSystemPackage(event.packageName)) {

                        unlocks++
                        Log.d(
                            "PresenceAI",
                            "Unlock inferred via foreground app: ${event.packageName}"
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