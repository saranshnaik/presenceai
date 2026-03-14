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
        val unlock_freq: Float,
        val micro_session: Float,
        val notification_reflex: Float,
        val behavior_drift_z: Float,
        val time_phase: Float,
        val vad_energy: Float,
        val ble_social: Float
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
        var unlocks = 0

        while (events.hasNextEvent()) {

            events.getNextEvent(event)

            // API 28+ KEYGUARD_HIDDEN (18) represents a screen unlock
            if (event.eventType == 18) {
                unlocks++
            }

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

        return computeMetrics(sessions, unlocks)
    }

    private fun computeMetrics(rawSessions: List<Session>, unlocks: Int): FeatureMetrics {

        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val timePhase = when (hour) {
            in 5..11 -> 0.2f
            in 12..16 -> 0.5f
            in 17..21 -> 0.8f
            else -> 0.1f
        }

        if (rawSessions.isEmpty()) {
            return FeatureMetrics(
                unlock_freq = unlocks.toFloat(),
                micro_session = 0f,
                notification_reflex = 0f,
                behavior_drift_z = 0f,
                time_phase = timePhase,
                vad_energy = 0f,
                ble_social = 0f
            )
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

        val microSessionRatio = if (sessionCount > 0) microSessions.toFloat() / sessionCount.toFloat() else 0f
        val notifReflexRatio = if (sessionCount > 0) notificationReflex.toFloat() / sessionCount.toFloat() else 0f

        Log.d("PresenceAI", "-------------")
        Log.d("PresenceAI", "Sessions: $sessionCount")
        Log.d("PresenceAI", "Unlocks (x1): $unlocks")
        Log.d("PresenceAI", "Micro Session Ratio (x2): $microSessionRatio")
        Log.d("PresenceAI", "Notification Reflex Ratio (x3): $notifReflexRatio")
        Log.d("PresenceAI", "Time Phase (x5): $timePhase")

        return FeatureMetrics(
            unlock_freq = unlocks.toFloat(),
            micro_session = microSessionRatio,
            notification_reflex = notifReflexRatio,
            behavior_drift_z = 0f,   // Feature x4 placeholder
            time_phase = timePhase,
            vad_energy = 0f,         // Feature x6 placeholder
            ble_social = 0f          // Feature x7 placeholder
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