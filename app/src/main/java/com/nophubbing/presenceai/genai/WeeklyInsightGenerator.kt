package com.nophubbing.presenceai.genai

<<<<<<< HEAD
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
=======
import android.util.Log

/**
 * Weekly stats aggregated from Room + BanditStore, passed to Claude.
 * genai/ never imports ml/ or rl/ directly.
 */
data class WeeklyStats(
    val avgPresenceScore: Float,       // 0–100
    val totalNudges: Int,
    val nudgesResponded: Int,          // positive label count
    val preferredFormat: String,       // "HAPTIC" or "NOTIFICATION"
    val hapticAvgReward: Float,
    val notifAvgReward: Float,
    val hapticTrials: Int,
    val notifTrials: Int,
    val avgUnlocksPerHour: Float,
    val avgMicroSessionRatio: Float,   // 0–1
    val peakRiskHour: Int,             // 0–23
    val daysTracked: Int
)

/**
 * WeeklyInsightGenerator — 3–4 sentence behavioral summary via Claude.
 *
 * Output: warm, second-person, coach-like prose.
 * Timeout: 10s (user-initiated from Insights screen)
 * Falls back to static string on failure. Never throws.
 */
object WeeklyInsightGenerator {

    private const val TAG = "WeeklyInsightGenerator"

    private val SYSTEM = """
        You are Presence AI, a warm behavioral mindfulness coach.
        The user has shared a week of phone-use data. Write 3–4 sentences of personal insight.
        
        Rules:
        - Second person ("you", "your")
        - Encouraging tone — celebrate wins, gently flag patterns
        - Mention which nudge format (haptic vs notification) works better for them if clear
        - Never use the word "phubbing"
        - Under 80 words total
        - No bullet points, no headers — flowing prose only
        - Output ONLY the insight text, no preamble
    """.trimIndent()

    suspend fun generateWeeklyInsight(stats: WeeklyStats): String {
        val userMsg = buildString {
            append("Week summary (${stats.daysTracked} days tracked):\n")
            append("- Average presence score: ${String.format("%.1f", stats.avgPresenceScore)}/100\n")
            append("- Total nudges delivered: ${stats.totalNudges}, responded to: ${stats.nudgesResponded}\n")
            append("- Haptic nudges: ${stats.hapticTrials} trials, avg reward: ${String.format("%.2f", stats.hapticAvgReward)}\n")
            append("- Notification nudges: ${stats.notifTrials} trials, avg reward: ${String.format("%.2f", stats.notifAvgReward)}\n")
            append("- Preferred format so far: ${stats.preferredFormat}\n")
            append("- Avg unlocks/hour: ${String.format("%.1f", stats.avgUnlocksPerHour)}\n")
            append("- Micro-session ratio: ${String.format("%.0f", stats.avgMicroSessionRatio * 100)}%\n")
            append("- Peak distraction hour: ${stats.peakRiskHour}:00\n")
        }

        return try {
            val result = ClaudeApiClient.complete(
                systemPrompt = SYSTEM,
                userMessage  = userMsg,
                maxTokens    = 150,
                timeoutMs    = 10_000
            )
            if (result.isBlank()) Fallbacks.WEEKLY_INSIGHT else result
>>>>>>> 9dcff341204a809012f0c63fe5b6648ca0cf4a9e
        } catch (e: Exception) {
            Log.e(TAG, "generateWeeklyInsight failed: ${e.message}")
            Fallbacks.WEEKLY_INSIGHT
        }
    }
}
