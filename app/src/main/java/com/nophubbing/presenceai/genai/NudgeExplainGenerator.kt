package com.nophubbing.presenceai.genai

import android.content.Context
import android.util.Log
import com.nophubbing.presenceai.ai.GeminiService

data class NudgeEvent(
    val unlockCount:   Int,
    val microSessions: Int,
    val notifReflex:   Boolean,
    val pPhub:         Float,
    val format:        String,
    val voicePresent:  Boolean,
    val blePresent:    Boolean,
    val hourOfDay:     Int,
    val behaviorDrift: Float
)

object NudgeExplainGenerator {
    private const val TAG = "NudgeExplainGenerator"

    suspend fun explainNudge(context: Context, event: NudgeEvent): String {
        return try {
            val prompt = buildString {
                append("Explain in 2 sentences why this presence nudge was sent. ")
                append("Be specific and warm. No 'phubbing'. Second person.\n")
                append("Data: unlocked ${event.unlockCount}x in 10 min")
                if (event.microSessions > 0) append(", ${event.microSessions} quick sessions under 30s")
                if (event.notifReflex) append(", reflexive notification checking")
                if (event.voicePresent) append(", voice detected nearby")
                if (event.blePresent) append(", BLE devices detected nearby")
                append(", presence score ${((1f - event.pPhub) * 100).toInt()}/100")
                append(", format: ${event.format}")
            }
            // Use Gemini chat for explanation
            GeminiService.chat(context, prompt).ifBlank { Fallbacks.NUDGE_EXPLAIN }
        } catch (e: Exception) {
            Log.e(TAG, "explainNudge failed: ${e.message}")
            Fallbacks.NUDGE_EXPLAIN
        }
    }
}
