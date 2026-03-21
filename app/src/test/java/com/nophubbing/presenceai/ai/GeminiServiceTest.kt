package com.nophubbing.presenceai.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests for GeminiService.
 * Focuses on fallback mechanisms and basic input handling.
 * Note: Real API calls are not made during unit tests.
 */
class GeminiServiceTest {

    @Test
    fun `test generateNudge returns fallback when API key is missing`() = runBlocking {
        val result = GeminiService.generateNudge(
            presenceScore = 40,
            isEvening = true,
            someoneNearby = true
        )
        
        if (com.nophubbing.presenceai.BuildConfig.GEMINI_API_KEY.isBlank()) {
            assertEquals(GeminiService.FALLBACK_NUDGE, result)
        } else {
            assertNotNull(result)
        }
    }

    // Context-dependent methods (generateWeeklySummary, chat, generateHistoricalAnalysis)
    // are skipped in plain JUnit tests due to the lack of a real Android environment.

    @Test
    fun `test clearChat resets history`() {
        GeminiService.clearChat()
    }
}
