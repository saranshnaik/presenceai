package com.nophubbing.presenceai.utils

import androidx.compose.ui.graphics.Color
import com.nophubbing.presenceai.ui.theme.PresenceColors

/**
 * PresenceState — maps a presence score (0–100) to a human-readable state
 * with an associated ring color and sub-label.
 *
 * Presence score = (1 - P(phub)) * 100
 *   ≥ 70 → Fully Present (green)
 *   40–69 → Slightly Distracted (amber)
 *   < 40 → High Distraction (coral/orange)
 */
enum class PresenceState(
    val label: String,
    val ringColor: Color,
    val subLabel: String
) {
    FULLY_PRESENT(
        "Fully Present",
        PresenceColors.ScoreLow,
        "You're doing great"
    ),
    SLIGHTLY_DISTRACTED(
        "Slightly Distracted",
        PresenceColors.ScoreMid,
        "Monitoring gently"
    ),
    HIGH_DISTRACTION(
        "High Distraction",
        PresenceColors.ScoreHigh,
        "Observing gracefully"
    )
}

fun presenceScoreToState(score: Int): PresenceState = when {
    score >= 70 -> PresenceState.FULLY_PRESENT
    score >= 40 -> PresenceState.SLIGHTLY_DISTRACTED
    else        -> PresenceState.HIGH_DISTRACTION
}

/** Presence score is just the inverse of P(phub). */
fun pPhubToPresenceScore(pPhub: Float): Int =
    ((1f - pPhub) * 100).toInt().coerceIn(0, 100)
