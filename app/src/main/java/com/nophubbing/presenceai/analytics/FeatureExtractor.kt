package com.nophubbing.presenceai.analytics

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log

class FeatureExtractor(private val context: Context) {

    data class Session(
        val packageName: String,
        val startTime: Long,
        var endTime: Long
    )

    data class FeatureMetrics(
        val sessionCount: Int,
        val avgDuration: Long,
        val microSessions: Int,
        val notificationReflex: Int
    )

    fun extractFeatures(windowMinutes: Int = 30): FeatureMetrics {

        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val endTime = System.currentTimeMillis()
        val startTime = endTime - (windowMinutes * 60 * 1000L)

        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()

        val sessions = mutableListOf<Session>()

        var currentPackage: String? = null
        var sessionStart = 0L

        while (events.hasNextEvent()) {

            events.getNextEvent(event)

            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {

                if (isSystemPackage(event.packageName)) continue

                currentPackage?.let {

                    val duration = event.timeStamp - sessionStart

                    if (duration > 0) {
                        sessions.add(
                            Session(
                                packageName = it,
                                startTime = sessionStart,
                                endTime = event.timeStamp
                            )
                        )
                    }
                }

                currentPackage = event.packageName
                sessionStart = event.timeStamp
            }
        }

        currentPackage?.let {

            val duration = endTime - sessionStart

            if (duration > 0) {
                sessions.add(
                    Session(
                        packageName = it,
                        startTime = sessionStart,
                        endTime = endTime
                    )
                )
            }
        }

        return computeMetrics(sessions)
    }

    private fun computeMetrics(rawSessions: List<Session>): FeatureMetrics {

        if (rawSessions.isEmpty()) {
            return FeatureMetrics(0, 0, 0, 0)
        }

        val mergedSessions = mutableListOf<Session>()

        var current = rawSessions[0]

        for (i in 1 until rawSessions.size) {

            val next = rawSessions[i]

            val gap = next.startTime - current.endTime

            if (next.packageName == current.packageName && gap < 3000) {

                current.endTime = next.endTime

            } else {

                mergedSessions.add(current)
                current = next
            }
        }

        mergedSessions.add(current)

        var totalDuration = 0L
        var microSessions = 0
        var notificationReflex = 0

        for (s in mergedSessions) {

            val duration = s.endTime - s.startTime

            totalDuration += duration

            if (duration < 20000) {
                microSessions++
            }

            if (duration < 5000) {
                notificationReflex++
            }

            Log.d(
                "PresenceAI",
                "Session: ${s.packageName} | duration(ms): $duration"
            )
        }

        val sessionCount = mergedSessions.size

        val avgDuration =
            if (sessionCount > 0) totalDuration / sessionCount else 0

        Log.d("PresenceAI", "-------------")
        Log.d("PresenceAI", "Sessions: $sessionCount")
        Log.d("PresenceAI", "Avg Duration(ms): $avgDuration")
        Log.d("PresenceAI", "Micro Sessions (<20s): $microSessions")
        Log.d("PresenceAI", "Notification Reflex (<5s): $notificationReflex")

        return FeatureMetrics(
            sessionCount = sessionCount,
            avgDuration = avgDuration,
            microSessions = microSessions,
            notificationReflex = notificationReflex
        )
    }

    private fun isSystemPackage(pkg: String): Boolean {

        val ignoredPrefixes = listOf(
            "com.miui.",
            "com.android.",
            "com.google.android.permissioncontroller",
            "com.google.android.packageinstaller",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.nophubbing.presenceai",
            "host.exp.exponent",        // Expo Go
            "expo.modules"              // Expo runtime modules
        )

        return ignoredPrefixes.any { pkg.startsWith(it) }
    }
}