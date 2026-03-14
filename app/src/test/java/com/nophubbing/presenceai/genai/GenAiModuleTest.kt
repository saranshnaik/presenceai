package com.nophubbing.presenceai.genai

import kotlinx.coroutines.runBlocking
import org.junit.Test

// ─────────────────────────────────────────────────────────────────────────────
//  HOW TO RUN
// ─────────────────────────────────────────────────────────────────────────────
//
//  1. ADD YOUR GEMINI API KEY
//     Open this file and replace the empty string in API_KEY with your key:
//
//         private const val API_KEY = "YOUR_GEMINI_API_KEY_HERE"
//
//     You can get a free key at https://aistudio.google.com/app/apikey
//
//  2. RUN THE TESTS
//     From the project root directory, execute:
//
//         .\gradlew :app:test --tests "com.nophubbing.presenceai.genai.GenAiModuleTest" --info
//
//     Or run the specific test methods:
//
//         .\gradlew :app:test --tests "com.nophubbing.presenceai.genai.GenAiModuleTest.testGenerateNudgeCopy" --info
//         .\gradlew :app:test --tests "com.nophubbing.presenceai.genai.GenAiModuleTest.testGenerateWeeklyInsight" --info
//         .\gradlew :app:test --tests "com.nophubbing.presenceai.genai.GenAiModuleTest.testExplainNudge" --info
//         .\gradlew :app:test --tests "com.nophubbing.presenceai.genai.GenAiModuleTest.testFallbackBehavior" --info
//
//     Results appear in:
//         app/build/reports/tests/testDebugUnitTest/index.html  (HTML report)
//         app/build/test-results/testDebugUnitTest/             (XML results)
//
//  3. RUN AS STANDALONE MAIN (optional, for quick console output)
//     From a Kotlin REPL or a standalone Kotlin script runner you can call:
//         main()
//     The main() function at the bottom of this file mirrors all test cases and
//     prints results to stdout.
//
// ─────────────────────────────────────────────────────────────────────────────

/** ⚠️  Replace with your actual Gemini API key before running. */
private const val API_KEY = "AIzaSyDmn0Cb96vfwyMkhcrdde1rYB8YvtQ8h2w"

// ─────────────────────────────────────────────────────────────
//  Shared Dummy Data
// ─────────────────────────────────────────────────────────────

/** A realistic nudge context: 5 unlocks in 10 min, BLE + VAD confirmed, 2nd nudge today. */
private val sampleNudgeContext = NudgeContext(
    unlockCount10min  = 5,
    windowMinutes     = 10,
    presenceScore     = 42,
    timePeriod        = "evening",
    dayType           = "weekday",
    patternNote       = "User has been unlocking phone repeatedly during dinner time.",
    bleConfirmed      = true,
    vadConfirmed      = true,
    nudgeCountToday   = 1
)

/** A realistic week: good overall trend, Thursday best, Wednesday worst, 3 PM most challenging. */
private val sampleWeeklyStats = WeeklyStats(
    avgPresenceScore     = 68,
    bestDay              = "Thursday",
    bestDayScore         = 89,
    worstDay             = "Wednesday",
    worstDayScore        = 41,
    mostChallengingHour  = "15",   // 24-hour; will be rendered as "3 PM" in the prompt
    nudgeCountTotal      = 14,
    nudgeAcceptanceRate  = 71,
    weekOnWeekChange     = 8,
    topTrigger           = "notification_reflex",
    modelUpdateCount     = 3
)

/** A realistic nudge event: notification reflex at 7 PM, high confidence, 45 min since last nudge. */
private val sampleNudgeEvent = NudgeEvent(
    unlockCount10min     = 6,
    notificationReflex   = true,
    hourOfDay            = 19,
    bleConfirmed         = true,
    vadConfirmed         = false,
    pPhub                = 0.87,
    timeSinceLastNudge   = 45
)

// ─────────────────────────────────────────────────────────────
//  JUnit Test Class
// ─────────────────────────────────────────────────────────────

class GenAiModuleTest {

    // ── Unit tests for utility functions (no network needed) ──────────────────

    @Test
    fun `cleanNudgeCopy removes surrounding quotes`() {
        val result = cleanNudgeCopy("\"Put the phone down and be present.\"")
        assert(!result.startsWith("\"")) { "Should remove leading quote" }
        assert(!result.endsWith("\"")) { "Should remove trailing quote" }
    }

    @Test
    fun `cleanNudgeCopy rejects text containing phubbing`() {
        val result = cleanNudgeCopy("Stop phubbing your partner.")
        assert(result.isEmpty()) { "Expected empty string when 'phubbing' appears in input" }
    }

    @Test
    fun `cleanNudgeCopy rejects text longer than 20 words`() {
        val longText = "This is a very long sentence that contains far more than twenty words " +
                "and should be rejected by the cleaning function."
        val result = cleanNudgeCopy(longText)
        assert(result.isEmpty()) { "Expected empty string for text > 20 words" }
    }

    @Test
    fun `cleanNudgeCopy strips common model prefixes`() {
        val result = cleanNudgeCopy("Sure: Look up from your phone.")
        assert(!result.lowercase().startsWith("sure:")) { "Should strip 'Sure:' prefix" }
    }

    @Test
    fun `cleanParagraph strips markdown characters`() {
        val raw = "**Great week!** You improved by _8 points_.\n# Heading\n- Bullet point"
        val result = cleanParagraph(raw)
        assert(!result.contains("*")) { "Should remove '*'" }
        assert(!result.contains("#")) { "Should remove '#'" }
        assert(!result.contains("_")) { "Should remove '_'" }
        assert(!result.contains("\n")) { "Should collapse newlines" }
    }

    @Test
    fun `cleanParagraph collapses multiple whitespace`() {
        val raw = "Word1   Word2\t\tWord3\n\nWord4"
        val result = cleanParagraph(raw)
        assert(!result.contains(Regex("\\s{2,}"))) { "Should collapse multiple whitespace to single space" }
    }

    @Test
    fun `triggerToHuman maps known triggers`() {
        assert(triggerToHuman("notification_reflex") == "Notification reflex")
        assert(triggerToHuman("unlock_freq") == "Frequent unlocking")
        assert(triggerToHuman("presence_drop") == "Presence score drop")
        assert(triggerToHuman("ble_proximity") == "Nearby device detected")
        assert(triggerToHuman("vad_speech") == "Conversation nearby")
        assert(triggerToHuman("session_length") == "Long phone session")
        assert(triggerToHuman("repeated_unlock") == "Repeated unlock pattern")
    }

    @Test
    fun `triggerToHuman handles unknown trigger`() {
        val result = triggerToHuman("some_unknown_trigger_xyz")
        assert(result == "Elevated phone activity") {
            "Unknown trigger should map to default. Got: '$result'"
        }
    }

    @Test
    fun `triggerToHuman is case-insensitive`() {
        val result = triggerToHuman("NOTIFICATION_REFLEX")
        assert(result == "Notification reflex") { "Should handle uppercase input. Got: '$result'" }
    }

    // ── Integration tests (require valid API_KEY to pass content assertions) ──

    @Test
    fun testGenerateNudgeCopy() = runBlocking {
        println("\n══════════════════════════════════════════")
        println("TEST: generateNudgeCopy")
        println("══════════════════════════════════════════")
        println("Input NudgeContext:")
        println("  unlockCount10min  = ${sampleNudgeContext.unlockCount10min}")
        println("  presenceScore     = ${sampleNudgeContext.presenceScore}")
        println("  timePeriod        = ${sampleNudgeContext.timePeriod}")
        println("  bleConfirmed      = ${sampleNudgeContext.bleConfirmed}")
        println("  vadConfirmed      = ${sampleNudgeContext.vadConfirmed}")
        println("  nudgeCountToday   = ${sampleNudgeContext.nudgeCountToday}")
        println()

        val result = generateNudgeCopy(sampleNudgeContext, API_KEY)
        println("Result: \"$result\"")

        // Assertions
        assert(result.isNotBlank()) { "Result should not be blank" }
        assert(!result.contains("phubbing", ignoreCase = true)) {
            "Result must not contain 'phubbing'"
        }
        val wordCount = result.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size
        assert(wordCount <= 20) { "Nudge copy should be at most 20 words (after cleaning), got $wordCount" }
        println("✓ Passed all assertions (${wordCount} words)")
    }

    @Test
    fun testGenerateWeeklyInsight() = runBlocking {
        println("\n══════════════════════════════════════════")
        println("TEST: generateWeeklyInsight")
        println("══════════════════════════════════════════")
        println("Input WeeklyStats:")
        println("  avgPresenceScore    = ${sampleWeeklyStats.avgPresenceScore}")
        println("  bestDay             = ${sampleWeeklyStats.bestDay} (score: ${sampleWeeklyStats.bestDayScore})")
        println("  worstDay            = ${sampleWeeklyStats.worstDay} (score: ${sampleWeeklyStats.worstDayScore})")
        println("  mostChallengingHour = ${sampleWeeklyStats.mostChallengingHour} → 3 PM")
        println("  topTrigger          = ${sampleWeeklyStats.topTrigger}")
        println()

        val result = generateWeeklyInsight(sampleWeeklyStats, API_KEY)
        println("Result:\n\"$result\"")

        assert(result.isNotBlank()) { "Weekly insight should not be blank" }
        assert(!result.contains("phubbing", ignoreCase = true)) {
            "Insight must not contain 'phubbing'"
        }
        println("✓ Passed all assertions")
    }

    @Test
    fun testExplainNudge() = runBlocking {
        println("\n══════════════════════════════════════════")
        println("TEST: explainNudge")
        println("══════════════════════════════════════════")
        println("Input NudgeEvent:")
        println("  unlockCount10min   = ${sampleNudgeEvent.unlockCount10min}")
        println("  notificationReflex = ${sampleNudgeEvent.notificationReflex}")
        println("  hourOfDay          = ${sampleNudgeEvent.hourOfDay} → 7:00 PM")
        println("  bleConfirmed       = ${sampleNudgeEvent.bleConfirmed}")
        println("  pPhub              = ${sampleNudgeEvent.pPhub}")
        println("  timeSinceLastNudge = ${sampleNudgeEvent.timeSinceLastNudge} min")
        println()

        val result = explainNudge(sampleNudgeEvent, API_KEY)
        println("Result:\n\"$result\"")

        assert(result.isNotBlank()) { "Nudge explanation should not be blank" }
        assert(!result.contains("phubbing", ignoreCase = true)) {
            "Explanation must not contain 'phubbing'"
        }
        println("✓ Passed all assertions")
    }

    @Test
    fun testFallbackBehavior() = runBlocking {
        println("\n══════════════════════════════════════════")
        println("TEST: Fallback behavior (invalid API key)")
        println("══════════════════════════════════════════")

        val badKey = "INVALID_KEY_TRIGGERS_FALLBACK"

        val nudgeFallback = generateNudgeCopy(sampleNudgeContext, badKey)
        println("generateNudgeCopy fallback  → \"$nudgeFallback\"")
        assert(nudgeFallback == FALLBACK_COPY) {
            "Expected FALLBACK_COPY with invalid key.\nGot: \"$nudgeFallback\""
        }

        val insightFallback = generateWeeklyInsight(sampleWeeklyStats, badKey)
        println("generateWeeklyInsight fallback → \"$insightFallback\"")
        assert(insightFallback == FALLBACK_INSIGHT) {
            "Expected FALLBACK_INSIGHT with invalid key.\nGot: \"$insightFallback\""
        }

        val explainFallback = explainNudge(sampleNudgeEvent, badKey)
        println("explainNudge fallback       → \"$explainFallback\"")
        assert(explainFallback == FALLBACK_EXPLANATION) {
            "Expected FALLBACK_EXPLANATION with invalid key.\nGot: \"$explainFallback\""
        }

        println("✓ All three fallbacks returned correctly")
    }
}

// ─────────────────────────────────────────────────────────────
//  Standalone main() – mirrors all test cases for quick console runs
// ─────────────────────────────────────────────────────────────

/**
 * Standalone entry point. Useful for running the module outside of Gradle / JUnit,
 * e.g., from a Kotlin script runner or a simple JVM main.
 *
 * Usage (with kotlinc installed):
 *   kotlinc GenAiModule.kt GenAiModuleTest.kt -include-runtime -d out.jar
 *   java -cp out.jar com.nophubbing.presenceai.genai.GenAiModuleTestKt
 */
fun main() = runBlocking {
    val separator = "═".repeat(50)

    // ── Run all test scenarios sequentially ──────────────────────────────────

    println(separator)
    println("RUNNING: GenAI Module – Console Test Runner")
    println(separator)

    // 1. Nudge copy
    println("\n[1] generateNudgeCopy")
    println("    Context: ${sampleNudgeContext.unlockCount10min} unlocks, " +
            "score=${sampleNudgeContext.presenceScore}, " +
            "BLE=${sampleNudgeContext.bleConfirmed}, " +
            "VAD=${sampleNudgeContext.vadConfirmed}")
    val nudgeCopy = generateNudgeCopy(sampleNudgeContext, API_KEY)
    println("    → \"$nudgeCopy\"")
    println("    Contains 'phubbing': ${nudgeCopy.contains("phubbing", ignoreCase = true)}")
    println("    Word count       : ${nudgeCopy.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size}")

    // 2. Weekly insight
    println("\n[2] generateWeeklyInsight")
    println("    Stats: avgScore=${sampleWeeklyStats.avgPresenceScore}, " +
            "best=${sampleWeeklyStats.bestDay}(${sampleWeeklyStats.bestDayScore}), " +
            "challengingHour=${sampleWeeklyStats.mostChallengingHour}")
    val weeklyInsight = generateWeeklyInsight(sampleWeeklyStats, API_KEY)
    println("    →")
    println("    \"$weeklyInsight\"")
    println("    Contains 'phubbing': ${weeklyInsight.contains("phubbing", ignoreCase = true)}")

    // 3. Nudge explanation
    println("\n[3] explainNudge")
    println("    Event: unlocks=${sampleNudgeEvent.unlockCount10min}, " +
            "hour=${sampleNudgeEvent.hourOfDay} (7:00 PM), " +
            "pPhub=${"%.2f".format(sampleNudgeEvent.pPhub)}")
    val explanation = explainNudge(sampleNudgeEvent, API_KEY)
    println("    →")
    println("    \"$explanation\"")
    println("    Contains 'phubbing': ${explanation.contains("phubbing", ignoreCase = true)}")

    // 4. Fallback (invalid key)
    println("\n[4] Fallback behavior (invalid API key)")
    val badKey = "INVALID_KEY_TRIGGERS_FALLBACK"
    val fallbackNudge   = generateNudgeCopy(sampleNudgeContext, badKey)
    val fallbackInsight = generateWeeklyInsight(sampleWeeklyStats, badKey)
    val fallbackExplain = explainNudge(sampleNudgeEvent, badKey)
    println("    generateNudgeCopy   → \"$fallbackNudge\"")
    println("    Matches fallback    : ${fallbackNudge == FALLBACK_COPY}")
    println("    generateWeeklyInsight → \"$fallbackInsight\"")
    println("    Matches fallback      : ${fallbackInsight == FALLBACK_INSIGHT}")
    println("    explainNudge        → \"$fallbackExplain\"")
    println("    Matches fallback    : ${fallbackExplain == FALLBACK_EXPLANATION}")

    println("\n$separator")
    println("All tests complete.")
    println(separator)
}
