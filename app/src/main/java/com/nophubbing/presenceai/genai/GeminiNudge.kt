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

const val FALLBACK_COPY        = "The person with you deserves your full attention."
const val FALLBACK_INSIGHT     = "Your presence data has been collected this week. Check back soon for insights."
const val FALLBACK_EXPLANATION = "The app noticed elevated phone activity while someone was nearby and sent a reminder."

fun cleanNudgeCopy(raw: String): String {
    var text = raw.trim()
    if ((text.startsWith("\"") && text.endsWith("\"")) ||
        (text.startsWith("'") && text.endsWith("'"))) {
        text = text.substring(1, text.length - 1).trim()
    }
    val prefixes = listOf("sure:", "here is:", "here's:", "nudge:", "message:", "copy:", "response:", "output:")
    for (prefix in prefixes) {
        if (text.lowercase().startsWith(prefix)) {
            text = text.substring(prefix.length).trim()
            break
        }
    }
    if (text.contains("phubbing", ignoreCase = true)) return ""
    val wordCount = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size
    if (wordCount > 20) return ""
    return text
}

fun cleanParagraph(raw: String): String {
    var text = raw.replace(Regex("[*#_`~]"), "")
    text = text.replace(Regex("\\s+"), " ").trim()
    return text
}

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

            val json       = JSONObject(responseText)
            val candidates = json.optJSONArray("candidates") ?: return@withContext ""
            if (candidates.length() == 0) return@withContext ""
            val content = candidates.getJSONObject(0).optJSONObject("content") ?: return@withContext ""
            val parts   = content.optJSONArray("parts") ?: return@withContext ""
            if (parts.length() == 0) return@withContext ""
            parts.getJSONObject(0).optString("text", "")
        } catch (e: Exception) {
            ""
        }
    }

suspend fun generateNudgeCopy(context: NudgeContext, apiKey: String): String {
    val contextDetails = buildString {
        append("- Phone unlocks in the last ${context.windowMinutes} minutes: ${context.unlockCount10min}\n")
        append("- Current presence score: ${context.presenceScore}/100\n")
        append("- Time of day: ${context.timePeriod} (${context.dayType})\n")
        if (context.patternNote.isNotBlank()) append("- Observed pattern: ${context.patternNote}\n")
        if (context.bleConfirmed) append("- A nearby Bluetooth device confirms someone is physically present.\n")
        if (context.vadConfirmed) append("- Voice activity detected, indicating a conversation is happening.\n")
        if (context.nudgeCountToday > 0) append("- This is nudge #${context.nudgeCountToday + 1} today.\n")
    }
    val prompt = """
        You are a mindfulness assistant helping people stay present with the people around them.
        Context: $contextDetails
        Write a single warm encouraging sentence (maximum 12 words) reminding the user to be present.
        Rules: One sentence, 12 words max. No "phubbing". No markdown. No prefix. Output ONLY the sentence.
    """.trimIndent()
    val raw = callGemini(prompt, apiKey)
    if (raw.isBlank()) return FALLBACK_COPY
    val cleaned = cleanNudgeCopy(raw)
    return if (cleaned.isBlank()) FALLBACK_COPY else cleaned
}

suspend fun generateWeeklyInsight(stats: WeeklyStats, apiKey: String): String {
    val challengingHourInt   = stats.mostChallengingHour.toIntOrNull() ?: -1
    val challengingHourLabel = if (challengingHourInt in 0..23) {
        val amPm  = if (challengingHourInt < 12) "AM" else "PM"
        val hour12 = when {
            challengingHourInt == 0  -> 12
            challengingHourInt <= 12 -> challengingHourInt
            else                     -> challengingHourInt - 12
        }
        "$hour12 $amPm"
    } else stats.mostChallengingHour
    val direction        = if (stats.weekOnWeekChange >= 0) "+${stats.weekOnWeekChange}" else "${stats.weekOnWeekChange}"
    val topTriggerHuman  = triggerToHuman(stats.topTrigger)
    val prompt = """
        You are a compassionate digital-wellbeing coach writing a weekly reflection.
        Stats: avg score ${stats.avgPresenceScore}/100 (${direction}pts vs last week),
        best day ${stats.bestDay} (${stats.bestDayScore}/100),
        hardest day ${stats.worstDay} (${stats.worstDayScore}/100),
        hardest hour $challengingHourLabel,
        ${stats.nudgeCountTotal} nudges (${stats.nudgeAcceptanceRate}% accepted),
        top trigger: $topTriggerHuman, model updates: ${stats.modelUpdateCount}.
        Write 3-4 sentences. Must mention "${stats.bestDay}" and "$challengingHourLabel".
        Warm, non-judgmental. No "phubbing". No markdown. Output ONLY the paragraph.
    """.trimIndent()
    val raw = callGemini(prompt, apiKey)
    if (raw.isBlank()) return FALLBACK_INSIGHT
    val cleaned = cleanParagraph(raw)
    return if (cleaned.isBlank()) FALLBACK_INSIGHT else cleaned
}

suspend fun explainNudge(event: NudgeEvent, apiKey: String): String {
    val amPm    = if (event.hourOfDay < 12) "AM" else "PM"
    val hour12  = when {
        event.hourOfDay == 0  -> 12
        event.hourOfDay <= 12 -> event.hourOfDay
        else                  -> event.hourOfDay - 12
    }
    val timeLabel = "$hour12:00 $amPm"
    val triggerDescription = buildString {
        if (event.notificationReflex) append("a notification-triggered unlock reflex, ")
        append("${event.unlockCount10min} unlocks in the last 10 minutes")
        if (event.bleConfirmed) append(", BLE confirmed someone nearby")
        if (event.vadConfirmed) append(", voice activity detected")
    }
    val prompt = """
        You are explaining to a user why their mindfulness app sent a gentle reminder.
        Time: $timeLabel. Signals: $triggerDescription. Confidence: ${"%.2f".format(event.pPhub)}.
        Minutes since last nudge: ${event.timeSinceLastNudge}.
        Write 2-3 sentences explaining kindly why the app sent the reminder.
        No "phubbing". No markdown. No prefix. Output ONLY the explanation.
    """.trimIndent()
    val raw = callGemini(prompt, apiKey)
    if (raw.isBlank()) return FALLBACK_EXPLANATION
    val cleaned = cleanParagraph(raw)
    return if (cleaned.isBlank()) FALLBACK_EXPLANATION else cleaned
}
