package com.nophubbing.presenceai.genai

import android.util.Log

/**
 * Context object carrying ML signals → Claude prompt.
 * Built by ContextBuilder, passed here. genai/ never imports ml/ or rl/.
 */
data class NudgeContext(
    val unlockCount: Int,
    val microSessions: Int,
    val notifReflex: Boolean,
    val pPhub: Float,           // 0–1
    val voicePresent: Boolean,
    val blePresent: Boolean,
    val hourOfDay: Int
)

/**
 * NudgeCopyGenerator — generates a ≤ 15-word warm nudge string via Claude.
 *
 * Rules enforced in system prompt:
 *  - Second person, warm, non-accusatory
 *  - Never use the word "phubbing"
 *  - ≤ 15 words
 *  - No emojis in copy (notification channel handles those)
 *
 * Falls back to static string on any failure. Never throws.
 */
object NudgeCopyGenerator {

    private const val TAG = "NudgeCopyGenerator"

    private val SYSTEM = """
        You are a warm, non-judgmental mindfulness assistant embedded in a presence app.
        Your ONLY job: write a single nudge reminder of ≤ 15 words.
        
        Rules (non-negotiable):
        - Second person ("you", "your") — never third person
        - Never use the word "phubbing" or any accusatory language
        - Warm, gentle, human tone — like a friend who cares
        - ≤ 15 words, no more
        - No emojis, no punctuation beyond a period
        - Output ONLY the nudge text, nothing else — no quotes, no labels
    """.trimIndent()

    suspend fun generateNudgeCopy(context: NudgeContext): String {
        val userMsg = buildString {
            append("Generate a nudge for someone who: ")
            append("unlocked their phone ${context.unlockCount} times in 10 minutes")
            if (context.microSessions > 0) append(", had ${context.microSessions} micro-sessions under 30 seconds")
            if (context.notifReflex) append(", reflexively checked notifications")
            if (context.voicePresent) append(", while someone is speaking nearby")
            if (context.blePresent) append(", with people nearby (BLE detected)")
            append(". Phubbing probability: ${String.format("%.0f", context.pPhub * 100)}%.")
            append(" Hour of day: ${context.hourOfDay}.")
        }

        return try {
            val result = ClaudeApiClient.complete(
                systemPrompt = SYSTEM,
                userMessage  = userMsg,
                maxTokens    = 40,
                timeoutMs    = 5_000
            )
            val copy = result.take(120).trim()
            if (copy.isBlank()) Fallbacks.NUDGE_COPY else copy
        } catch (e: Exception) {
            Log.e(TAG, "generateNudgeCopy failed: ${e.message}")
            Fallbacks.NUDGE_COPY
        }
    }
}
