package com.nophubbing.presenceai.ai

import android.content.Context
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
//import com.nophubbing.presenceai.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * GeminiService — Handles interaction with the Google Gemini Pro model for generating
 * behavioral insights based on presence and usage data.
 */
object GeminiService {

    // IMPORTANT: User needs to provide their API key
    private const val MODEL_NAME = "gemini-2.5-flash"

    private val model by lazy {
        GenerativeModel(
            modelName = MODEL_NAME,
            apiKey = "AIzaSyC5at3LmCZVqMYzqf692rOW_F1ye7XjpbM"
        )
    }

    suspend fun generateInsights(summaryData: String): String = withContext(Dispatchers.IO) {

        val prompt = """
            You are "Presence AI", a mindful behavioral coach. 
            Analyze the following device usage data summary and provide 3-4 concise, helpful insights.
            Focus on trends like phubbing risk, session intensity, and presence score improvements.
            Be encouraging but direct about areas needing attention.
            
            USER DATA SUMMARY:
            $summaryData
            
            Format the output with clear bullet points. Keep it under 150 words.
        """.trimIndent()

        try {
            val response = model.generateContent(
                content { text(prompt) }
            )
            response.text ?: "AI could not generate insights at this time."
        } catch (e: Exception) {
            Log.e("GeminiService", "Error generating insights", e)
            "Error connecting to Gemini API: ${e.message}"
        }
    }

    /**
     * Reads the hourly summary file and generates insights.
     */
    suspend fun generateHourlyInsights(context: android.content.Context): String {
        // 1. Refresh the summary file (writes to c:/Users/Apurav/AndroidStudioProjects/PresenceAI/app/files/hourly_summary.txt)
        val summaryText = HourlySummarizer.refreshSummary(context)
        
        // 2. Pass the content of that summary directly to Gemini
        return generateInsights(summaryText)
    }
}
