package com.nophubbing.presenceai.analytics

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import java.util.Calendar

class FeatureExtractor(private val context: Context) {

    data class Session(
        val packageName: String,
        val startTime: Long,
        var endTime: Long
    )

    data class FeatureMetrics(
        val microSessions: Int,
        val notificationReflex: Int,
        val behaviorDrift: Float,
        val timePhase: Int
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

            when (event.eventType) {

                UsageEvents.Event.MOVE_TO_FOREGROUND -> {

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

                UsageEvents.Event.MOVE_TO_BACKGROUND -> {

                    if (currentPackage == event.packageName) {

                        val duration = event.timeStamp - sessionStart

                        if (duration > 0) {
                            sessions.add(
                                Session(
                                    packageName = event.packageName,
                                    startTime = sessionStart,
                                    endTime = event.timeStamp
                                )
                            )
                        }

                        currentPackage = null
                    }
                }
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
            return FeatureMetrics(
                microSessions = 0,
                notificationReflex = 0,
                behaviorDrift = 0f,
                timePhase = computeTimePhase()
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

        var microSessions = 0
        var notificationReflex = 0

        var previousEnd = mergedSessions[0].endTime

        for (i in mergedSessions.indices) {

            val s = mergedSessions[i]

            val duration = s.endTime - s.startTime
            val gap = s.startTime - previousEnd

            // Micro-session: short duration + frequent checking
            if (duration < 15000 && gap < 60000) {
                microSessions++
            }

            // Notification reflex: session starts shortly after last notification
            val reactionDelay = s.startTime - NotificationTracker.lastNotificationTime
            if (reactionDelay in 0..5000) {
                notificationReflex++
            }

            // #region agent log
            try {
                val logEntry = """
{"sessionId":"86e374","runId":"pre-fix","hypothesisId":"H1","location":"FeatureExtractor.kt:146","message":"session_metrics","data":{"index":$i,"packageName":"${s.packageName}","startTime":${s.startTime},"endTime":${s.endTime},"duration":$duration,"gap":$gap,"lastNotificationTime":${NotificationTracker.lastNotificationTime},"reactionDelay":$reactionDelay,"isMicro":${duration < 15000 && gap < 60000},"notificationReflexCount":$notificationReflex},"timestamp":${System.currentTimeMillis()}}
""".trimIndent()
                java.io.File("debug-86e374.log").appendText(logEntry + "\n")
            } catch (_: Exception) {
            }
            // #endregion

            previousEnd = s.endTime

            Log.d(
                "PresenceAI",
                "Session: ${s.packageName} duration(ms): $duration"
            )
        }

        val behaviorDrift =
            computeBehaviorDrift(microSessions, mergedSessions.size)

        Log.d("PresenceAI", "-------------")
        Log.d("PresenceAI", "Micro Sessions (<15s + rapid reopen): $microSessions")
        Log.d("PresenceAI", "Notification Reflex (<5s sessions): $notificationReflex")
        Log.d("PresenceAI", "Behavior Drift: $behaviorDrift")

        return FeatureMetrics(
            microSessions = microSessions,
            notificationReflex = notificationReflex,
            behaviorDrift = behaviorDrift,
            timePhase = computeTimePhase()
        )
    }

    private fun computeBehaviorDrift(
        microSessions: Int,
        sessionCount: Int
    ): Float {

        if (sessionCount == 0) return 0f

        return microSessions.toFloat() / sessionCount
    }

    private fun computeTimePhase(): Int {

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        return when (hour) {
            in 5..11 -> 0
            in 12..16 -> 1
            in 17..21 -> 2
            else -> 3
        }
    }

    private fun isSystemPackage(pkg: String): Boolean {

        val ignoredPrefixes = listOf(
            "com.miui.",
            "com.android.",
            "com.google.android.permissioncontroller",
            "com.google.android.packageinstaller",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.nophubbing.presenceai"
        )

        return ignoredPrefixes.any { pkg.startsWith(it) }
    }
}