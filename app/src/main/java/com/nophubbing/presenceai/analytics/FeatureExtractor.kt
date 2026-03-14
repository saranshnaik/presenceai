package com.nophubbing.presenceai.analytics

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log

/**
 * FeatureExtractor.kt — reads real device signals from UsageStatsManager.
 * Produces FeatureMetrics with ALL fields required by the 14-feature ML model.
 *
 * Fields added vs original:
 *  - avgMicroSessionDurationS  (was missing → always 0 in old code)
 *  - avgNotifToUnlockGapS      (was missing → always 0 in old code)
 *  - unlockCountPerHour        (was unlock_freq; renamed to match schema)
 *  - vadEnergy / bleSocial     (floats; set by VoiceMonitor / BleScanner externally)
 */
class FeatureExtractor(private val context: Context) {

    data class Session(val packageName: String, val startTime: Long, var endTime: Long) {
        val durationMs get() = endTime - startTime
    }

    data class FeatureMetrics(
        // Core behavioral
        val unlockCountPerHour: Float,         // x5
        val microSessionRatio: Float,           // derived → x6 ratio
        val notifReflexRatio: Float,            // derived → x7 ratio
        val timePhase: Float,                   // x9

        // Duration-based (new — needed for 14-feature model)
        val avgMicroSessionDurationS: Float,    // x6 absolute value
        val avgNotifToUnlockGapS: Float,        // x7 absolute value

        // Social context (populated by VoiceMonitor / BleScanner)
        val vadEnergy: Float,                   // x10/x12
        val bleSocial: Float,                   // x11/x13

        // Counts for UI
        val totalSessions: Int,
        val microSessionCount: Int,
        val notifReflexCount: Int,

        // Category breakdown — new
        val categoryBreakdown: List<AppCategoryClassifier.CategoryBreakdown> = emptyList(),
        val dominantCategory: AppCategoryClassifier.Category? = null
    )

    /**
     * External setters — called by VoiceMonitor and BleScanner before extractFeatures().
     * Defaults to 0.0 when permissions not granted.
     */
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

        if (rawSessions.isEmpty()) {
            Log.d("PresenceAI", "Sessions: 0 | Unlocks: $unlocks")
            return emptyMetrics().copy(unlockCountPerHour = unlocksPerHour, timePhase = timePhase)
        }

        // Merge adjacent same-package sessions with gap < 3s
        val merged = mutableListOf<Session>()
        var cur = rawSessions[0]
        for (i in 1 until rawSessions.size) {
            val next = rawSessions[i]
            if (next.packageName == cur.packageName && (next.startTime - cur.endTime) < 3_000) {
                cur.endTime = next.endTime
            } else { merged.add(cur); cur = next }
        }
        merged.add(cur)

        val micro = mutableListOf<Session>()   // < 20s
        val reflex = mutableListOf<Session>()  // < 5s (notification reflex)

        merged.forEach { s ->
            val durMs = s.durationMs
            if (durMs < 20_000) micro.add(s)
            if (durMs < 5_000)  reflex.add(s)
            Log.d("PresenceAI", "Session: ${s.packageName} | duration(ms): $durMs")
        }

        val total = merged.size
        val microRatio  = if (total > 0) micro.size.toFloat() / total else 0f
        val reflexRatio = if (total > 0) reflex.size.toFloat() / total else 0f

        val avgMicroDurS = if (micro.isNotEmpty())
            micro.sumOf { it.durationMs }.toFloat() / micro.size / 1_000f else 0f

        // Estimate notif-to-unlock gap from reflex sessions
        val avgNotifGapS = if (reflex.isNotEmpty())
            reflex.sumOf { it.durationMs }.toFloat() / reflex.size / 1_000f else 0f

        Log.d("PresenceAI", "-------------")
        Log.d("PresenceAI", "Sessions: $total")
        Log.d("PresenceAI", "Unlocks (x5): $unlocksPerHour/hr")
        Log.d("PresenceAI", "Micro Session Ratio (x6): $microRatio")
        Log.d("PresenceAI", "Notification Reflex Ratio (x7): $reflexRatio")
        Log.d("PresenceAI", "Time Phase (x9): $timePhase")
        Log.d("PresenceAI", "Signals aggregated: VAD=${currentVadEnergy.toInt()}, Prox=${if (currentBleSocial > 0) 1 else -1}, Unlocks=$unlocksPerHour")

        val breakdown = AppCategoryClassifier.buildBreakdown(merged)
        return FeatureMetrics(
            unlockCountPerHour       = unlocksPerHour,
            microSessionRatio        = microRatio,
            notifReflexRatio         = reflexRatio,
            timePhase                = timePhase,
            avgMicroSessionDurationS = avgMicroDurS,
            avgNotifToUnlockGapS     = avgNotifGapS,
            vadEnergy                = currentVadEnergy,
            bleSocial                = currentBleSocial,
            totalSessions            = total,
            microSessionCount        = micro.size,
            notifReflexCount         = reflex.size,
            categoryBreakdown        = breakdown,
            dominantCategory         = breakdown.firstOrNull()?.category
        )
    }

    private fun emptyMetrics() = FeatureMetrics(
        unlockCountPerHour = 0f, microSessionRatio = 0f, notifReflexRatio = 0f,
        timePhase = 0.4f, avgMicroSessionDurationS = 0f, avgNotifToUnlockGapS = 0f,
        vadEnergy = 0f, bleSocial = 0f, totalSessions = 0, microSessionCount = 0, notifReflexCount = 0,
        categoryBreakdown = emptyList(), dominantCategory = null
    )

    private fun isSystemPackage(pkg: String): Boolean {
        val ignored = listOf(
            "com.miui.", "com.android.", "com.coloros.", "com.oppo.",
            "com.google.android.permissioncontroller", "com.google.android.packageinstaller",
            "com.google.android.gms", "com.google.android.gsf",
            "com.nophubbing.presenceai"
        )
        return ignored.any { pkg.startsWith(it) }
    }
}
