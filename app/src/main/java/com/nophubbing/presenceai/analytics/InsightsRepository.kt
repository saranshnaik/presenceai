package com.nophubbing.presenceai.analytics

import android.content.Context
import com.nophubbing.presenceai.storage.CSVReader
import java.text.SimpleDateFormat
import java.util.*

data class UsageStats(
    val avgPresenceScore: Float,
    val totalUnlocks: Int,
    val avgPhubbingRisk: Float,
    val microSessionRatio: Float,
    val dayNumber: Int
)

/**
 * InsightsRepository — Aggregates CSV data into daily and weekly summaries for AI analysis.
 */
object InsightsRepository {

    fun getDailySummary(context: Context): List<UsageStats> {
        val allRows = CSVReader.readAllAsSignalRows(context)
        if (allRows.isEmpty()) return emptyList()

        return allRows.groupBy { it.dayNumber }
            .map { (day, rows) ->
                UsageStats(
                    avgPresenceScore = (100.0 - rows.averageBy { it.label * 100.0 }).toFloat(), // Simplified mapping
                    totalUnlocks = rows.sumOf { it.unlockCountPerHour.toInt() } / 12, // Rough estimate per hour slots
                    avgPhubbingRisk = rows.averageBy { it.label }.toFloat(),
                    microSessionRatio = rows.count { it.microSessionDurationS < 20 }.toFloat() / rows.size.coerceAtLeast(1),
                    dayNumber = day
                )
            }
            .sortedBy { it.dayNumber }
    }

    private fun <T> List<T>.averageBy(selector: (T) -> Double): Double {
        if (isEmpty()) return 0.0
        return sumOf(selector) / size
    }

    fun getWeeklyComparison(context: Context): String {
        val daily = getDailySummary(context)
        if (daily.size < 2) return "Not enough data for comparison yet."

        val currentDay = daily.last()
        val prevDay = daily[daily.size - 2]
        
        val presenceDiff = currentDay.avgPresenceScore - prevDay.avgPresenceScore
        val unlockDiff = currentDay.totalUnlocks - prevDay.totalUnlocks

        return """
            Day ${currentDay.dayNumber} vs Day ${prevDay.dayNumber}:
            Presence Score: ${String.format("%.1f", currentDay.avgPresenceScore)} (${if(presenceDiff >= 0) "+" else ""}${String.format("%.1f", presenceDiff)})
            Total Unlocks: ${currentDay.totalUnlocks} (${if(unlockDiff >= 0) "+" else ""}$unlockDiff)
            Micro-session Ratio: ${String.format("%.1f%%", currentDay.microSessionRatio * 100)}
        """.trimIndent()
    }
}
