package com.nophubbing.presenceai.analytics

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import java.util.Calendar

/**
 * FeatureExtractor.kt — reads real device signals from UsageStatsManager.
 */
class FeatureExtractor(private val context: Context) {

    data class Session(val packageName: String, val startTime: Long, val endTime: Long) {
        val durationMs get() = endTime - startTime
    }

    data class FeatureMetrics(
        val unlockCountPerHour: Float,
        val rawUnlockCount: Int,          // actual unlocks in the window (not extrapolated)
        val microSessionRatio: Float,
        val notifReflexRatio: Float,
        val timePhase: Float,
        val avgMicroSessionDurationS: Float,
        val avgNotifToUnlockGapS: Float,
        val vadEnergy: Float,
        val bleSocial: Float,
        val totalSessions: Int,
        val microSessionCount: Int,
        val notifReflexCount: Int,
        val categoryBreakdown: List<AppCategoryClassifier.CategoryBreakdown> = emptyList(),
        val dominantCategory: AppCategoryClassifier.Category? = null
    )

    var currentVadEnergy: Float = 0f
    var currentBleSocial: Float = 0f

    fun extractFeatures(windowMinutes: Int = 10): FeatureMetrics {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val start = now - windowMinutes * 60_000L

            val events = usm.queryEvents(start, now)
            val event = UsageEvents.Event()
            val sessions = mutableListOf<Session>()
            var currentPkg: String? = null
            var sessionStart = 0L
            var unlocks = 0

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == 18) unlocks++   // KEYGUARD_HIDDEN = unlock
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    if (isSystemPackage(event.packageName)) continue
                    currentPkg?.let {
                        val dur = event.timeStamp - sessionStart
                        if (dur > 0) sessions.add(Session(it, sessionStart, event.timeStamp))
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == 18) unlocks++   // KEYGUARD_HIDDEN = unlock
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    if (isSystemPackage(event.packageName)) continue
                    currentPkg?.let {
                        val dur = event.timeStamp - sessionStart
                        if (dur > 0) sessions.add(Session(it, sessionStart, event.timeStamp))
                    }
                    currentPkg = event.packageName
                    sessionStart = event.timeStamp
                }
            }
            currentPkg?.let {
                val dur = now - sessionStart
                if (dur > 0) sessions.add(Session(it, sessionStart, now))
            }

            computeMetrics(sessions, unlocks, windowMinutes)
        } catch (e: Exception) {
            Log.e("PresenceAI", "FeatureExtractor error: ${e.message}")
            emptyMetrics()
        }
    }

    private fun computeMetrics(
        rawSessions: List<Session>,
        unlocks: Int,
        windowMinutes: Int
    ): FeatureMetrics {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val timePhase = when (hour) {
            in 0..4   -> 0.1f
            in 5..11  -> 0.2f
            in 12..16 -> 0.5f
            in 17..21 -> 0.8f
            else      -> 0.6f
        }
        val unlocksPerHour = unlocks.toFloat() * (60f / windowMinutes)

        // Detailed session logs for debugging
        Log.d("PresenceAI", "-------------")
        rawSessions.forEach { s ->
            Log.d("PresenceAI", "Session: ${s.packageName} | duration(ms): ${s.durationMs}")
        }

        if (rawSessions.isEmpty()) {
            Log.d("PresenceAI", "Sessions: 0 | Unlocks: $unlocks")
            return emptyMetrics().copy(unlockCountPerHour = unlocksPerHour, rawUnlockCount = unlocks, timePhase = timePhase)
        }

        // ── Two separate lists serve two different purposes ────────────────────
        //
        // rawSessions = individual app-switch events as recorded by UsageStats.
        //   Used for: totalSessions (app switch count), micro-session detection,
        //   notification-reflex detection, avgMicroDurS, avgNotifGapS.
        //   These should NEVER be merged — each switch is a discrete behaviour.
        //
        // mergedForCategory = same-package entries collapsed only when they are
        //   re-entries within 500 ms (e.g. activity rotation, dialog dismiss).
        //   Used ONLY for AppCategoryClassifier duration totals.
        //   500 ms is tight enough to avoid collapsing real separate sessions.

        val mergedForCategory = mutableListOf<Session>()
        var cur = rawSessions[0].copy()   // copy so we don't mutate the original
        for (i in 1 until rawSessions.size) {
            val next = rawSessions[i]
            // Only collapse if same package AND gap < 500 ms (true re-entry artifact)
            if (next.packageName == cur.packageName && (next.startTime - cur.endTime) < 500) {
                cur = cur.copy(endTime = next.endTime)
            } else {
                mergedForCategory.add(cur)
                cur = next.copy()
            }
        }
        mergedForCategory.add(cur)

        // Micro and reflex detection operates on raw individual app switches
        val micro  = rawSessions.filter { it.durationMs in 1..19_999 }
        val reflex = rawSessions.filter { it.durationMs in 1..4_999 }

        val totalRaw    = rawSessions.size
        val microRatio  = if (totalRaw > 0) micro.size.toFloat() / totalRaw else 0f
        val reflexRatio = if (totalRaw > 0) reflex.size.toFloat() / totalRaw else 0f

        val avgMicroDurS = if (micro.isNotEmpty())
            micro.sumOf { it.durationMs }.toFloat() / micro.size / 1_000f else 0f

        val avgNotifGapS = if (reflex.isNotEmpty())
            reflex.sumOf { it.durationMs }.toFloat() / reflex.size / 1_000f else 0f

        Log.d("PresenceAI", "Sessions (raw switches): $totalRaw")
        Log.d("PresenceAI", "Unlocks (x5): $unlocksPerHour/hr")
        Log.d("PresenceAI", "Micro Sessions (<20s): ${micro.size}  ratio=${"%.2f".format(microRatio)}")
        Log.d("PresenceAI", "Notif Reflex (<5s): ${reflex.size}  ratio=${"%.2f".format(reflexRatio)}")
        Log.d("PresenceAI", "Time Phase (x9): $timePhase")
        Log.d("PresenceAI", "Signals aggregated: VAD=$currentVadEnergy, Prox=$currentBleSocial")

        val breakdown = AppCategoryClassifier.buildBreakdown(mergedForCategory)
        return FeatureMetrics(
            unlockCountPerHour       = unlocksPerHour,
            rawUnlockCount           = unlocks,
            microSessionRatio        = microRatio,
            notifReflexRatio         = reflexRatio,
            timePhase                = timePhase,
            avgMicroSessionDurationS = avgMicroDurS,
            avgNotifToUnlockGapS     = avgNotifGapS,
            vadEnergy                = currentVadEnergy,
            bleSocial                = currentBleSocial,
            totalSessions            = totalRaw,
            microSessionCount        = micro.size,
            notifReflexCount         = reflex.size,
            categoryBreakdown        = breakdown,
            dominantCategory         = breakdown.firstOrNull()?.category
        )
    }

    private fun emptyMetrics() = FeatureMetrics(
        unlockCountPerHour = 0f, rawUnlockCount = 0, microSessionRatio = 0f, notifReflexRatio = 0f,
        timePhase = 0.4f, avgMicroSessionDurationS = 0f, avgNotifToUnlockGapS = 0f,
        vadEnergy = 0f, bleSocial = 0f, totalSessions = 0, microSessionCount = 0, notifReflexCount = 0,
        categoryBreakdown = emptyList(), dominantCategory = null
    )

    private fun isSystemPackage(pkg: String): Boolean {
        val ignored = listOf("com.miui.", "com.android.", "com.coloros.", "com.oppo.", "com.google.android.", "com.nophubbing.presenceai")
        return ignored.any { pkg.startsWith(it) }
    }
}

