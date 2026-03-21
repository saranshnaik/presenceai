package com.nophubbing.presenceai.genai

import android.util.Log
import com.nophubbing.presenceai.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * ClaudeApiClient — Retrofit-free, zero-throw Claude API wrapper.
 *
 * Model: claude-sonnet-4-20250514 (always)
 * Key:   BuildConfig.CLAUDE_API_KEY from local.properties — never hardcoded.
 *
 * Contract: NEVER throws. Returns "" on any failure so callers always get a String.
 */
object ClaudeApiClient {

    private const val TAG = "ClaudeApiClient"
    private const val BASE_URL = "https://api.anthropic.com/v1/messages"
    private const val MODEL = "claude-sonnet-4-20250514"
    private const val ANTHROPIC_VERSION = "2023-06-01"

    /**
     * Send a single prompt to Claude and return the response text.
     *
     * @param systemPrompt  System context for the model (tone, persona, constraints)
     * @param userMessage   The user-turn message
     * @param maxTokens     Max response tokens (default 256 — enough for nudge copy)
     * @param timeoutMs     Connection + read timeout in ms
     * @return Response string, or "" on any failure
     */
    suspend fun complete(
        systemPrompt: String,
        userMessage: String,
        maxTokens: Int = 256,
        timeoutMs: Int = 5_000
    ): String = withContext(Dispatchers.IO) {
        try {
            val apiKey = BuildConfig.CLAUDE_API_KEY
            if (apiKey.isBlank()) {
                Log.w(TAG, "CLAUDE_API_KEY not set in BuildConfig")
                return@withContext ""
            }

            val body = JSONObject().apply {
                put("model", MODEL)
                put("max_tokens", maxTokens)
                put("system", systemPrompt)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userMessage)
                    })
                })
            }.toString()

            val url = URL(BASE_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-api-key", apiKey)
                setRequestProperty("anthropic-version", ANTHROPIC_VERSION)
                doOutput = true
            }

            OutputStreamWriter(conn.outputStream).use { it.write(body) }

            val responseCode = conn.responseCode
            val responseBody = if (responseCode == 200) {
                conn.inputStream.bufferedReader().readText()
            } else {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
                Log.e(TAG, "HTTP $responseCode: $err")
                return@withContext ""
            }

            // Parse: content[0].text
            val json = JSONObject(responseBody)
            json.getJSONArray("content")
                .getJSONObject(0)
                .getString("text")
                .trim()

        } catch (e: Exception) {
            Log.e(TAG, "ClaudeApiClient.complete failed: ${e.message}")
            ""
        }
    }
}
