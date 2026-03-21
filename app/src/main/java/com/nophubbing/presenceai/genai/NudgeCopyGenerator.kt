package com.nophubbing.presenceai.genai

import android.util.Log
import com.nophubbing.presenceai.ai.GeminiService

data class NudgeContext(
    val unlockCount:   Int,
    val microSessions: Int,
    val notifReflex:   Boolean,
    val pPhub:         Float,
    val voicePresent:  Boolean,
    val blePresent:    Boolean,
    val hourOfDay:     Int
)

object NudgeCopyGenerator {
    private const val TAG = "NudgeCopyGenerator"

    suspend fun generateNudgeCopy(ctx: NudgeContext): String {
        return try {
            GeminiService.generateNudge(
                presenceScore = ((1f - ctx.pPhub) * 100).toInt(),
                isEvening     = ctx.hourOfDay >= 18 || ctx.hourOfDay < 6,
                someoneNearby = ctx.voicePresent || ctx.blePresent
            ).ifBlank { Fallbacks.NUDGE_COPY }
        } catch (e: Exception) {
            Log.e(TAG, "generateNudgeCopy failed: ${e.message}")
            Fallbacks.NUDGE_COPY
        }
    }
}
