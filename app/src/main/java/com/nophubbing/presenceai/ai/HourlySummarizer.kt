package com.nophubbing.presenceai.ai

import android.content.Context
import android.util.Log
import com.nophubbing.presenceai.storage.CSVReader
import java.io.File
import java.util.Locale

/**
 * HourlySummarizer — aggregates per-minute CSV logs into hourly blocks
 * and writes a textual summary for Gemini Pro analysis.
 */
object HourlySummarizer {

    private const val SUMMARY_FILE_NAME = "hourly_summary.txt"

    /**
     * Reads all historical data, groups by hour, and writes a concise text summary.
     */
    fun refreshSummary(context: Context): String {
        val rows = CSVReader.readAllAsSignalRows(context)
        if (rows.isEmpty()) return "No data available for summarization."

        // Group by hourOfDay
        val hourlyBlocks = rows.groupBy { it.hourOfDay }
        
        val summaryBuilder = StringBuilder()
        summaryBuilder.append("Behavioral Summary by Hour:\n\n")

        hourlyBlocks.keys.sorted().forEach { hour ->
            val block = hourlyBlocks[hour]!!
            val avgPhub = block.map { it.label }.filter { it != -1.0 }.average().takeIf { !it.isNaN() } ?: 0.0
            val avgUnlocks = block.averageBy { it.unlockCountPerHour }
            val avgDrift = block.averageBy { it.behaviorDriftScore }
            val totalMicRes = block.count { it.microSessionDurationS > 0 }
            
            val hourRange = String.format(Locale.US, "%02d:00 - %02d:00", hour, (hour + 1) % 24)
            summaryBuilder.append("- $hourRange: Phubbing Prob: ${String.format(Locale.US, "%.2f", avgPhub)}, ")
            summaryBuilder.append("Unlocks/Hr: ${String.format(Locale.US, "%.1f", avgUnlocks)}, ")
            summaryBuilder.append("Drift: ${String.format(Locale.US, "%.2f", avgDrift)}, ")
            summaryBuilder.append("Micro-sessions: $totalMicRes\n")
        }

        val summaryText = summaryBuilder.toString()
        
        try {
            val file = File(context.filesDir, SUMMARY_FILE_NAME)
            file.writeText(summaryText)
            Log.d("HourlySummarizer", "Saved summary to ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("HourlySummarizer", "Failed to write summary file", e)
        }

        return summaryText
    }

    /**
     * Reads the existing summary from disk.
     */
    fun getSavedSummary(context: Context): String {
        val file = File(context.filesDir, SUMMARY_FILE_NAME)
        return if (file.exists()) file.readText() else ""
    }

    private fun <T> List<T>.averageBy(selector: (T) -> Double): Double {
        if (isEmpty()) return 0.0
        return sumOf(selector) / size
    }
}
