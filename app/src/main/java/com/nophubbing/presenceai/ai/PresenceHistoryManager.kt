package com.nophubbing.presenceai.ai

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class WeeklyHistory(
    val weekLabel: String,
    val avgScore: Int,
    val bestDay: String,
    val bestScore: Int,
    val worstDay: String,
    val nudgeCount: Int,
    val acceptanceRate: Int
)

/**
 * PresenceHistoryManager — Manages the storage of weekly presence data in a JSON file.
 * Automatically generates synthetic data on first run to resolve the "cold start" problem.
 */
object PresenceHistoryManager {
    private const val TAG = "PresenceHistoryManager"
    private const val FILE_NAME = "presence_history.json"

    /**
     * Fetches the raw JSON string from storage.
     * If the file doesn't exist, it creates it with synthetic data.
     */
    fun getHistoryJson(context: Context): String {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) {
            Log.d(TAG, "Creating storage file with synthetic data.")
            val syntheticData = generateSyntheticData()
            file.writeText(syntheticData.toString(4))
            return syntheticData.toString()
        }
        return file.readText()
    }

    /**
     * Cold Start Resolution: Generates 3 weeks of synthetic historical data.
     */
    private fun generateSyntheticData(): JSONArray {
        val history = JSONArray()

        // Week 1: Low-to-moderate presence
        history.put(JSONObject().apply {
            put("weekLabel", "Feb 23 - Mar 1")
            put("avgScore", 58)
            put("bestDay", "Tuesday")
            put("bestScore", 78)
            put("worstDay", "Friday")
            put("nudgeCount", 18)
            put("acceptanceRate", 45)
        })

        // Week 2: Showing improvement
        history.put(JSONObject().apply {
            put("weekLabel", "Mar 2 - Mar 8")
            put("avgScore", 65)
            put("bestDay", "Sunday")
            put("bestScore", 88)
            put("worstDay", "Monday")
            put("nudgeCount", 12)
            put("acceptanceRate", 66)
        })

        // Week 3: Strong stability
        history.put(JSONObject().apply {
            put("weekLabel", "Mar 9 - Mar 14")
            put("avgScore", 72)
            put("bestDay", "Saturday")
            put("bestScore", 92)
            put("worstDay", "Thursday")
            put("nudgeCount", 9)
            put("acceptanceRate", 82)
        })

        return history
    }

    /**
     * Returns a human-readable summary of the historical data for AI context.
     */
    fun getAIPromptContext(context: Context): String {
        return try {
            val json = JSONArray(getHistoryJson(context))
            val sb = StringBuilder()
            sb.append("USER HISTORICAL CONTEXT (Past 3 Weeks):\n")
            for (i in 0 until json.length()) {
                val obj = json.getJSONObject(i)
                sb.append("- ${obj.getString("weekLabel")}: ")
                sb.append("Avg Score ${obj.getInt("avgScore")}, ")
                sb.append("Peak Day ${obj.getString("bestDay")}, ")
                sb.append("Nudges Respond Rate ${obj.getInt("acceptanceRate")}%\n")
            }
            sb.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error formatting AI context", e)
            "No historical data available."
        }
    }

    fun getHistoryList(context: Context): List<WeeklyHistory> {
        return try {
            val json = JSONArray(getHistoryJson(context))
            List(json.length()) { i ->
                val obj = json.getJSONObject(i)
                WeeklyHistory(
                    weekLabel = obj.getString("weekLabel"),
                    avgScore = obj.getInt("avgScore"),
                    bestDay = obj.getString("bestDay"),
                    bestScore = obj.getInt("bestScore"),
                    worstDay = obj.getString("worstDay"),
                    nudgeCount = obj.getInt("nudgeCount"),
                    acceptanceRate = obj.getInt("acceptanceRate")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing history list", e)
            emptyList()
        }
    }
}
