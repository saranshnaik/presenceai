package com.nophubbing.presenceai.genai

import android.content.Context
import android.util.Log
import com.nophubbing.presenceai.ai.GeminiService

data class WeeklyStats(
    val avgPresenceScore:     Float,
    val totalNudges:          Int,
    val nudgesResponded:      Int,
    val preferredFormat:      String,
    val hapticAvgReward:      Float,
    val notifAvgReward:       Float,
    val hapticTrials:         Int,
    val notifTrials:          Int,
    val avgUnlocksPerHour:    Float,
    val avgMicroSessionRatio: Float,
    val peakRiskHour:         Int,
    val daysTracked:          Int
)

object WeeklyInsightGenerator {
    private const val TAG = "WeeklyInsightGenerator"

    suspend fun generateWeeklyInsight(context: Context, stats: WeeklyStats): String {
        return try {
            val bestFormat = if (stats.hapticAvgReward >= stats.notifAvgReward) "haptic" else "notification"
            val acceptPct  = if (stats.totalNudges > 0)
                (stats.nudgesResponded * 100 / stats.totalNudges) else 0

            GeminiService.generateWeeklySummary(
                context        = context,
                avgScore       = stats.avgPresenceScore.toInt(),
                bestDay        = "Day ${stats.daysTracked}",
                worstDay       = "peak risk at ${stats.peakRiskHour}:00",
                nudgeCount     = stats.totalNudges,
                acceptanceRate = acceptPct
            ).ifBlank { Fallbacks.WEEKLY_INSIGHT }
        } catch (e: Exception) {
            Log.e(TAG, "generateWeeklyInsight failed: ${e.message}")
            Fallbacks.WEEKLY_INSIGHT
        }
    }
}
