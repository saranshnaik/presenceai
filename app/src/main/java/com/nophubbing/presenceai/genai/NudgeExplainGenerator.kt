package com.nophubbing.presenceai.genai

import android.util.Log

/**
 * Event context for "Why did I get this nudge?" explanation.
 */
data class NudgeEvent(
    val unlockCount: Int,
    val microSessions: Int,
    val notifReflex: Boolean,
    val pPhub: Float,
    val format: String,        // "HAPTIC" or "NOTIFICATION"
    val voicePresent: Boolean,
    val blePresent: Boolean,
    val hourOfDay: Int,
    val behaviorDrift: Float   // 0–1 z-score normalised
)

/**
 * NudgeExplainGenerator — "Why did I get this?" explanation.
 * 2–3 sentences. Concrete, factual, warm.
 * Timeout: 8s. Never throws. Fallback on failure.
 */
object NudgeExplainGenerator {

    private const val TAG = "NudgeExplainGenerator"

    private val SYSTEM = """
        You are Presence AI. Explain briefly why the app sent a presence reminder.
        
        Rules:
        - 2–3 sentences maximum
        - Use specific numbers from the data (e.g. "You unlocked 8 times in 10 minutes")
        - Warm, non-judgmental, factual tone
        - Never use the word "phubbing"
        - Second person
        - No emojis
        - Output ONLY the explanation, nothing else
    """.trimIndent()

    suspend fun explainNudge(event: NudgeEvent): String {
        val userMsg = buildString {
            append("Explain why this nudge was sent:\n")
            append("- Phone unlocked ${event.unlockCount} times in last 10 min\n")
            if (event.microSessions > 0) append("- ${event.microSessions} sessions were under 30 seconds\n")
            if (event.notifReflex) append("- Reflexively opened phone after notification\n")
            append("- Overall distraction probability: ${String.format("%.0f", event.pPhub * 100)}%\n")
            append("- Behavior drift from baseline: ${String.format("%.0f", event.behaviorDrift * 100)}%\n")
            if (event.voicePresent) append("- Voice activity detected (someone was speaking)\n")
            if (event.blePresent) append("- Bluetooth devices detected nearby (social context)\n")
            append("- Nudge format used: ${event.format}\n")
            append("- Time: ${event.hourOfDay}:00\n")
        }

        return try {
            val result = ClaudeApiClient.complete(
                systemPrompt = SYSTEM,
                userMessage  = userMsg,
                maxTokens    = 100,
                timeoutMs    = 8_000
            )
            if (result.isBlank()) Fallbacks.NUDGE_EXPLAIN else result
        } catch (e: Exception) {
            Log.e(TAG, "explainNudge failed: ${e.message}")
            Fallbacks.NUDGE_EXPLAIN
        }
    }
}
