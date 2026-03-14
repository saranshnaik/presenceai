package com.nophubbing.presenceai.genai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Contextual snapshot used to generate a real-time nudge message.
 *
 * @param unlockCount10min  Number of phone unlocks in the last 10 minutes.
 * @param windowMinutes     Rolling window size used for unlock counting.
 * @param presenceScore     Current presence score (0–100).
 * @param timePeriod        Human-readable period label (e.g. "morning", "evening").
 * @param dayType           "weekday" or "weekend".
 * @param patternNote       Free-text note about observed behavioural pattern.
 * @param bleConfirmed      Whether a nearby BLE device confirmed someone is present.
 * @param vadConfirmed      Whether Voice Activity Detection confirmed speech nearby.
 * @param nudgeCountToday   How many nudges have already been sent today.
 */
data class NudgeContext(
    val unlockCount10min: Int,
    val windowMinutes: Int,
    val presenceScore: Int,
    val timePeriod: String,
    val dayType: String,
    val patternNote: String,
    val bleConfirmed: Boolean,
    val vadConfirmed: Boolean,
    val nudgeCountToday: Int
)

/**
 * Aggregated weekly statistics used to generate a reflective insight paragraph.
 *
 * @param avgPresenceScore      Average presence score over the week (0–100).
 * @param bestDay               Name of the day with the highest presence score.
 * @param bestDayScore          Presence score on the best day.
 * @param worstDay              Name of the day with the lowest presence score.
 * @param worstDayScore         Presence score on the worst day.
 * @param mostChallengingHour   24-hour label for the hour with most unlocks (e.g. "14").
 * @param nudgeCountTotal       Total nudges sent during the week.
 * @param nudgeAcceptanceRate   Percentage of nudges the user acknowledged (0–100).
 * @param weekOnWeekChange      Week-over-week delta in average presence score (can be negative).
 * @param topTrigger            Internal key of the most frequent nudge trigger.
 * @param modelUpdateCount      How many times the local ML model updated this week.
 */
data class WeeklyStats(
    val avgPresenceScore: Int,
    val bestDay: String,
    val bestDayScore: Int,
    val worstDay: String,
    val worstDayScore: Int,
    val mostChallengingHour: String,
    val nudgeCountTotal: Int,
    val nudgeAcceptanceRate: Int,
    val weekOnWeekChange: Int,
    val topTrigger: String,
    val modelUpdateCount: Int
)

/**
 * Per-nudge event payload used to generate an educational explanation.
 *
 * @param unlockCount10min      Unlocks in the 10-minute window before the nudge fired.
 * @param notificationReflex    True if the event was triggered by a notification-reflex pattern.
 * @param hourOfDay             24-hour clock value at the time of the nudge (0–23).
 * @param bleConfirmed          Whether BLE confirmed presence.
 * @param vadConfirmed          Whether VAD confirmed speech.
 * @param pPhub                 Classifier probability that triggered the nudge (0.0–1.0).
 * @param timeSinceLastNudge    Minutes since the previous nudge was sent.
 */
data class NudgeEvent(
    val unlockCount10min: Int,
    val notificationReflex: Boolean,
    val hourOfDay: Int,
    val bleConfirmed: Boolean,
    val vadConfirmed: Boolean,
    val pPhub: Double,
    val timeSinceLastNudge: Int
)

/** Shown in place of a generated nudge when the API call fails or output is invalid. */
const val FALLBACK_COPY = "The person with you deserves your full attention."

/** Shown when weekly insight generation fails. */
const val FALLBACK_INSIGHT =
    "Your presence data has been collected this week. Check back soon for insights."

/** Shown when nudge explanation generation fails. */
const val FALLBACK_EXPLANATION =
    "The app noticed elevated phone activity while someone was nearby and sent a reminder."

/**
 * Cleans raw nudge copy returned by the model.
 *
 * Rules applied in order:
 * 1. Strip surrounding whitespace.
 * 2. Remove enclosing quotation marks (single or double).
 * 3. Remove common model-output prefixes such as "Sure:", "Here is:", "Nudge:", etc.
 * 4. Strip whitespace again.
 * 5. Return [FALLBACK_COPY] if the remaining text:
 *    - contains the word "phubbing" (case-insensitive), or
 *    - exceeds 20 words.
 * 6. Return the cleaned string on success, or "" to signal the caller to use the fallback.
 */
fun cleanNudgeCopy(raw: String): String {
    var text = raw.trim()

    // Remove surrounding quotes
    if ((text.startsWith("\"") && text.endsWith("\"")) ||
        (text.startsWith("'") && text.endsWith("'"))) {
        text = text.substring(1, text.length - 1).trim()
    }

    // Strip common prefixes the model sometimes prepends
    val prefixes = listOf(
        "sure:", "here is:", "here's:", "nudge:", "message:", "copy:", "response:", "output:"
    )
    for (prefix in prefixes) {
        if (text.lowercase().startsWith(prefix)) {
            text = text.substring(prefix.length).trim()
            break
        }
    }

    // Reject if it contains the banned word
    if (text.contains("phubbing", ignoreCase = true)) return ""

    // Reject if it exceeds the 20-word limit
    val wordCount = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size
    if (wordCount > 20) return ""

    return text
}

/**
 * Cleans a multi-sentence paragraph returned by the model.
 *
 * - Strips all markdown formatting characters (**, *, #, _, `, ~).
 * - Replaces newlines and multiple consecutive spaces with a single space.
 * - Returns the trimmed, plain-text paragraph.
 */
fun cleanParagraph(raw: String): String {
    // Remove markdown bold/italic/heading/code/strikethrough markers
    var text = raw.replace(Regex("[*#_`~]"), "")
    // Collapse newlines and whitespace runs
    text = text.replace(Regex("\\s+"), " ").trim()
    return text
}

/**
 * Converts an internal trigger key to a user-readable label.
 *
 * | Internal key            | Human-readable label          |
 * |-------------------------|-------------------------------|
 * | notification_reflex     | Notification reflex           |
 * | unlock_freq             | Frequent unlocking            |
 * | presence_drop           | Presence score drop           |
 * | ble_proximity           | Nearby device detected        |
 * | vad_speech              | Conversation nearby           |
 * | session_length          | Long phone session            |
 * | repeated_unlock         | Repeated unlock pattern       |
 * | (anything else)         | Elevated phone activity       |
 */
fun triggerToHuman(trigger: String): String = when (trigger.lowercase().trim()) {
    "notification_reflex" -> "Notification reflex"
    "unlock_freq"         -> "Frequent unlocking"
    "presence_drop"       -> "Presence score drop"
    "ble_proximity"       -> "Nearby device detected"
    "vad_speech"          -> "Conversation nearby"
    "session_length"      -> "Long phone session"
    "repeated_unlock"     -> "Repeated unlock pattern"
    else                  -> "Elevated phone activity"
}

private const val GEMINI_ENDPOINT =
    "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"

/**
 * Sends a single-turn prompt to the Gemini API and returns the raw text of the first candidate.
 * Returns an empty string on any network or JSON error.
 */
private suspend fun callGemini(prompt: String, apiKey: String): String =
    withContext(Dispatchers.IO) {
        try {
            val url = URL("$GEMINI_ENDPOINT?key=$apiKey")
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 20_000
            }

            // Build request body
            val requestBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                    put("maxOutputTokens", 256)
                })
            }.toString()

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(requestBody)
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                return@withContext ""
            }

            val responseText = connection.inputStream.bufferedReader(Charsets.UTF_8).readText()
            connection.disconnect()

            // Parse: candidates[0].content.parts[0].text
            val json = JSONObject(responseText)
            val candidates = json.optJSONArray("candidates") ?: return@withContext ""
            if (candidates.length() == 0) return@withContext ""
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content") ?: return@withContext ""
            val parts = content.optJSONArray("parts") ?: return@withContext ""
            if (parts.length() == 0) return@withContext ""
            parts.getJSONObject(0).optString("text", "")
        } catch (e: Exception) {
            // Catch all network / JSON / IO exceptions – never crash the caller
            ""
        }
    }

// ─────────────────────────────────────────────────────────────
//  Public Suspend Functions
// ─────────────────────────────────────────────────────────────

/**
 * Generates a short, warm nudge message tailored to the current [NudgeContext].
 *
 * The prompt instructs Gemini to produce at most 12 words in a single, empathetic sentence.
 * Additional context (BLE/VAD/nudge count) is injected conditionally.
 *
 * @return The cleaned nudge copy, or [FALLBACK_COPY] on failure or invalid output.
 */
suspend fun generateNudgeCopy(context: NudgeContext, apiKey: String): String {
    val contextDetails = buildString {
        append("- Phone unlocks in the last ${context.windowMinutes} minutes: ${context.unlockCount10min}\n")
        append("- Current presence score: ${context.presenceScore}/100\n")
        append("- Time of day: ${context.timePeriod} (${context.dayType})\n")
        if (context.patternNote.isNotBlank()) {
            append("- Observed pattern: ${context.patternNote}\n")
        }
        if (context.bleConfirmed) {
            append("- A nearby Bluetooth device confirms someone is physically present.\n")
        }
        if (context.vadConfirmed) {
            append("- Voice activity has been detected, indicating a conversation is happening.\n")
        }
        if (context.nudgeCountToday > 0) {
            append("- This is nudge #${context.nudgeCountToday + 1} today for this user.\n")
        }
    }

    val prompt = """
        You are a mindfulness assistant helping people stay present with the people around them.
        
        Context about the current situation:
        $contextDetails
        
        Write a single, warm, and encouraging sentence (maximum 12 words) reminding the user to 
        be present with the person near them. 
        
        Rules:
        - Exactly one sentence, 12 words maximum.
        - Do NOT use the word "phubbing" or any clinical/judgmental language.
        - Do NOT use markdown formatting.
        - Do NOT include any prefix like "Sure:" or "Here is:".
        - Output ONLY the nudge sentence itself, nothing else.
    """.trimIndent()

    val raw = callGemini(prompt, apiKey)
    if (raw.isBlank()) return FALLBACK_COPY
    val cleaned = cleanNudgeCopy(raw)
    return if (cleaned.isBlank()) FALLBACK_COPY else cleaned
}

/**
 * Generates a 3–4 sentence weekly reflection paragraph from [WeeklyStats].
 *
 * The output must mention the user's best day and the most challenging hour by name.
 *
 * @return The cleaned insight paragraph, or [FALLBACK_INSIGHT] on failure.
 */
suspend fun generateWeeklyInsight(stats: WeeklyStats, apiKey: String): String {
    val challengingHourInt = stats.mostChallengingHour.toIntOrNull() ?: -1
    val challengingHourLabel = if (challengingHourInt in 0..23) {
        val amPm = if (challengingHourInt < 12) "AM" else "PM"
        val hour12 = when {
            challengingHourInt == 0  -> 12
            challengingHourInt <= 12 -> challengingHourInt
            else                     -> challengingHourInt - 12
        }
        "$hour12 $amPm"
    } else {
        stats.mostChallengingHour
    }

    val direction = if (stats.weekOnWeekChange >= 0) "+${stats.weekOnWeekChange}" else "${stats.weekOnWeekChange}"
    val topTriggerHuman = triggerToHuman(stats.topTrigger)

    val prompt = """
        You are a compassionate digital-wellbeing coach writing a weekly reflection for a user.
        
        Here are the user's stats for this week:
        - Average presence score: ${stats.avgPresenceScore}/100 (change from last week: $direction points)
        - Best day: ${stats.bestDay} with a score of ${stats.bestDayScore}/100
        - Most challenging day: ${stats.worstDay} with a score of ${stats.worstDayScore}/100
        - Most challenging hour of the day: $challengingHourLabel
        - Total nudges sent: ${stats.nudgeCountTotal}, acceptance rate: ${stats.nudgeAcceptanceRate}%
        - Most common nudge trigger: $topTriggerHuman
        - Model personalisation updates: ${stats.modelUpdateCount}
        
        Write a 3–4 sentence weekly reflection for the user. 
        
        Rules:
        - You MUST explicitly mention "${stats.bestDay}" as the best day.
        - You MUST explicitly mention "$challengingHourLabel" as the most challenging hour.
        - Use a warm, encouraging, non-judgmental tone.
        - Do NOT use the word "phubbing" or similar clinical terms.
        - Do NOT use markdown (no bullet points, bold, headers, etc.).
        - Output ONLY the paragraph, nothing else — no labels, no prefix.
    """.trimIndent()

    val raw = callGemini(prompt, apiKey)
    if (raw.isBlank()) return FALLBACK_INSIGHT
    val cleaned = cleanParagraph(raw)
    return if (cleaned.isBlank()) FALLBACK_INSIGHT else cleaned
}

/**
 * Generates a 2–3 sentence educational explanation of why a nudge fired for the given [NudgeEvent].
 *
 * The [NudgeEvent.hourOfDay] (24-hour) is converted to a 12-hour AM/PM label before prompting.
 *
 * @return The cleaned explanation, or [FALLBACK_EXPLANATION] on failure.
 */
suspend fun explainNudge(event: NudgeEvent, apiKey: String): String {
    // Convert 24-hour to 12-hour AM/PM
    val amPm = if (event.hourOfDay < 12) "AM" else "PM"
    val hour12 = when {
        event.hourOfDay == 0  -> 12
        event.hourOfDay <= 12 -> event.hourOfDay
        else                  -> event.hourOfDay - 12
    }
    val timeLabel = "$hour12:00 $amPm"

    val triggerDescription = buildString {
        if (event.notificationReflex) append("a notification-triggered unlock reflex, ")
        append("${event.unlockCount10min} phone unlocks in the last 10 minutes")
        if (event.bleConfirmed) append(", a confirmed nearby device (BLE)")
        if (event.vadConfirmed) append(", detected conversation audio (VAD)")
    }

    val prompt = """
        You are an educational AI explaining to a user why their mindfulness app sent them a 
        gentle reminder to be present.
        
        Details about the nudge event:
        - Time of nudge: $timeLabel
        - Signals detected: $triggerDescription
        - App confidence score (0–1): ${"%.2f".format(event.pPhub)}
        - Minutes since the previous nudge: ${event.timeSinceLastNudge}
        
        Write 2–3 sentences that clearly and kindly explain why the app sent the reminder 
        at this moment, based on the signals above.
        
        Rules:
        - Write from the perspective of the app explaining itself to the user.
        - Do NOT use the word "phubbing" or any shaming language.
        - Do NOT use markdown formatting.
        - Do NOT include any prefix or label — output ONLY the explanation sentences.
    """.trimIndent()

    val raw = callGemini(prompt, apiKey)
    if (raw.isBlank()) return FALLBACK_EXPLANATION
    val cleaned = cleanParagraph(raw)
    return if (cleaned.isBlank()) FALLBACK_EXPLANATION else cleaned
}
