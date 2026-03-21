package com.nophubbing.presenceai.genai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * ClaudeApiClient — zero-throw Claude API wrapper.
 *
 * API key: set CLAUDE_API_KEY in local.properties, then in build.gradle:
 *   buildConfigField("String", "CLAUDE_API_KEY", "\"${localProperties["CLAUDE_API_KEY"]}\"")
 *
 * Falls back gracefully if key is blank or network fails.
 */
object ClaudeApiClient {

    private const val TAG              = "ClaudeApiClient"
    private const val BASE_URL         = "https://api.anthropic.com/v1/messages"
    private const val MODEL            = "claude-sonnet-4-20250514"
    private const val ANTHROPIC_VERSION = "2023-06-01"

    /**
     * Reads the API key at runtime from BuildConfig.
     * Returns "" if field is not present (prevents crash on missing config).
     */
    private fun apiKey(): String = try {
        val cls   = Class.forName("com.nophubbing.presenceai.BuildConfig")
        val field = cls.getField("CLAUDE_API_KEY")
        (field.get(null) as? String) ?: ""
    } catch (_: Exception) { "" }

    suspend fun complete(
        systemPrompt: String,
        userMessage:  String,
        maxTokens:    Int = 256,
        timeoutMs:    Int = 5_000
    ): String = withContext(Dispatchers.IO) {
        try {
            val key = apiKey()
            if (key.isBlank()) {
                Log.w(TAG, "CLAUDE_API_KEY not configured — using fallback")
                return@withContext ""
            }

            val body = JSONObject().apply {
                put("model",      MODEL)
                put("max_tokens", maxTokens)
                put("system",     systemPrompt)
                put("messages",   JSONArray().apply {
                    put(JSONObject().apply {
                        put("role",    "user")
                        put("content", userMessage)
                    })
                })
            }.toString()

            val conn = (URL(BASE_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = timeoutMs
                readTimeout    = timeoutMs
                setRequestProperty("Content-Type",       "application/json")
                setRequestProperty("x-api-key",          key)
                setRequestProperty("anthropic-version",  ANTHROPIC_VERSION)
                doOutput = true
            }
            OutputStreamWriter(conn.outputStream).use { it.write(body) }

            if (conn.responseCode != 200) {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
                Log.e(TAG, "HTTP ${conn.responseCode}: $err")
                return@withContext ""
            }

            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            json.getJSONArray("content").getJSONObject(0).getString("text").trim()

        } catch (e: Exception) {
            Log.e(TAG, "complete failed: ${e.message}")
            ""
        }
    }
}
