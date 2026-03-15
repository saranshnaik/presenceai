package com.nophubbing.presenceai.ai

import android.content.Context
import android.content.Context
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.nophubbing.presenceai.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GeminiService {

    private const val TAG = "GeminiService"
    private const val MODEL_NAME = "gemini-2.5-flash"

    private val model by lazy {
        GenerativeModel(
            modelName   = MODEL_NAME,
            apiKey      = BuildConfig.GEMINI_API_KEY,
            generationConfig = generationConfig {
                temperature = 0.7f
            }
        )
    }

    // Chatbot keeps its own conversation history
    private val chatHistory = mutableListOf<Pair<String, String>>() // user → model
    private val chat by lazy { model.startChat() }

    // ── Guard ─────────────────────────────────────────────────────────
    private fun apiKeyMissing() = BuildConfig.GEMINI_API_KEY.isBlank()

    // ─────────────────────────────────────────────────────────────────
    // 1. NUDGE COPY
    // Called when P(phub) > 0.65. Returns one warm sentence ≤ 12 words.
    // Input: presenceScore (0-100), isEvening (bool), someoneNearby (bool)
    // ─────────────────────────────────────────────────────────────────
    suspend fun generateNudge(
        presenceScore: Int,
        isEvening:     Boolean,
        someoneNearby: Boolean
    ): String = withContext(Dispatchers.IO) {
        if (apiKeyMissing()) return@withContext FALLBACK_NUDGE

        val prompt = """
You are Presence AI, a warm mindful coach in a phone app.
The user's presence score is $presenceScore/100.
${if (someoneNearby) "Someone is physically nearby." else ""}
${if (isEvening) "It is evening." else ""}

Write ONE gentle nudge message. Maximum 12 words.
Warm tone. Second person. No quotes. No explanation. No "phubbing".
Output only the message.
        """.trimIndent()

        try {
            val response = model.generateContent(content { text(prompt) })
            val raw = response.text?.trim() ?: return@withContext FALLBACK_NUDGE
            // Strip quotes if model adds them
            raw.removePrefix("\"").removeSuffix("\"")
               .removePrefix("'").removeSuffix("'")
               .trim()
               .ifBlank { FALLBACK_NUDGE }
        } catch (e: Exception) {
            Log.e(TAG, "generateNudge failed", e)
            FALLBACK_NUDGE
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 2. WEEKLY SUMMARY
    // Called once per week from InsightsRepository.
    // Input: avgScore, bestDay, worstDay, nudgeCount, acceptanceRate
    // ─────────────────────────────────────────────────────────────────
    suspend fun generateWeeklySummary(
        context:        Context,
        avgScore:       Int,
        bestDay:        String,
        worstDay:       String,
        nudgeCount:     Int,
        acceptanceRate: Int      // 0–100
    ): String = withContext(Dispatchers.IO) {
        if (apiKeyMissing()) return@withContext FALLBACK_SUMMARY

        val historyContext = PresenceHistoryManager.getAIPromptContext(context)

        val prompt = """
You are Presence AI writing a weekly summary for a user. 
Warm, encouraging, second person. No bullet points. No "phubbing".

$historyContext

Current Week Stats:
- Average presence score: $avgScore/100
- Best day: $bestDay
- Hardest day: $worstDay
- Nudges received: $nudgeCount
- Responded positively to: $acceptanceRate% of nudges

Write 3 sentences comparing this week to the history. Mention $bestDay. End with one actionable tip for next week.
Output only the paragraph.
        """.trimIndent()

        try {
            val response = model.generateContent(content { text(prompt) })
            response.text?.trim()?.ifBlank { FALLBACK_SUMMARY } ?: FALLBACK_SUMMARY
        } catch (e: Exception) {
            Log.e(TAG, "generateWeeklySummary failed", e)
            FALLBACK_SUMMARY
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 2.5 HISTORICAL ANALYSIS (NEW)
    // Analyzes the long-term trends from the history file.
    // ─────────────────────────────────────────────────────────────────
    suspend fun generateHistoricalAnalysis(context: Context): String = withContext(Dispatchers.IO) {
        if (apiKeyMissing()) return@withContext "Analysis unavailable: API key not configured."

        val historyContext = PresenceHistoryManager.getAIPromptContext(context)

        val prompt = """
Analyze the user's progress over the last month based on this data:
$historyContext

Focus on:
1. Improvement in average presence scores.
2. Consistency of the 'Best Day'.
3. Nudge acceptance trends.

Provide a 100-word analysis that highlights their biggest milestone and one area for growth.
Be encouraging and use a "coaching" tone.
        """.trimIndent()

        try {
            val response = model.generateContent(content { text(prompt) })
            response.text ?: "Could not generate trend analysis."
        } catch (e: Exception) {
            Log.e(TAG, "generateHistoricalAnalysis failed", e)
            "Error connecting to AI: ${e.message}"
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 3. CHATBOT
    // Multi-turn conversation. Maintains history within the session.
    // Call clearChat() on session end.
    // Input: userMessage (plain text from user)
    // ─────────────────────────────────────────────────────────────────
    suspend fun chat(context: Context, userMessage: String): String = withContext(Dispatchers.IO) {
        if (apiKeyMissing()) return@withContext "API key not configured."
        if (userMessage.isBlank()) return@withContext "Ask me anything about your presence habits."

        // System context injected only on first message
        val messageToSend = if (chatHistory.isEmpty()) {
            val historyContext = PresenceHistoryManager.getAIPromptContext(context)
            """You are Presence AI, a mindful companion helping users be more present with people around them.
You give short, warm, practical advice. Never use the word "phubbing". Keep responses under 80 words.

$historyContext

User: $userMessage""".trimIndent()
        } else {
            userMessage
        }

        return@withContext try {
            val response = chat.sendMessage(
                content { text(messageToSend) }
            )
            val reply = response.text?.trim() ?: FALLBACK_CHAT
            chatHistory.add(Pair(userMessage, reply))
            reply
        } catch (e: Exception) {
            Log.e(TAG, "chat failed", e)
            FALLBACK_CHAT
        }
    }

    fun clearChat() {
        chatHistory.clear()
    }

    // ─────────────────────────────────────────────────────────────────
    // 4. EXISTING — updated to include historical context
    // ─────────────────────────────────────────────────────────────────
    suspend fun generateInsights(context: Context, summaryData: String): String = withContext(Dispatchers.IO) {
        if (apiKeyMissing()) {
            return@withContext "API Key missing. Please set GEMINI_API_KEY in local.properties."
        }

        val historyContext = PresenceHistoryManager.getAIPromptContext(context)

        val prompt = """
You are "Presence AI", a mindful behavioral coach.
$historyContext

Analyze the following device usage data summary and provide 3-4 concise, helpful insights.
Compare this recent data to the history provided above to identify trends.
Focus on trends like phubbing risk, session intensity, and presence score improvements.
Be encouraging but direct about areas needing attention.

USER DATA SUMMARY:
$summaryData

Format the output with clear bullet points. Keep it under 150 words.
        """.trimIndent()
        try {
            val response = model.generateContent(content { text(prompt) })
            response.text ?: "AI could not generate insights at this time."
        } catch (e: Exception) {
            Log.e(TAG, "Error generating insights", e)
            "Error connecting to Gemini API: ${e.message}"
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Fallbacks — shown when API fails or key is missing
    // ─────────────────────────────────────────────────────────────────
    const val FALLBACK_NUDGE   = "The person with you deserves your full attention."
    const val FALLBACK_SUMMARY = "Your presence data has been collected. Check back soon for your weekly insight."
    const val FALLBACK_CHAT    = "I'm having trouble connecting right now. Try again in a moment."
}
