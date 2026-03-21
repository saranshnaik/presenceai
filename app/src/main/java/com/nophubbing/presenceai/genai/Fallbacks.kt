package com.nophubbing.presenceai.genai

/**
 * Fallbacks — static strings used when Claude API is unavailable.
 * These are the last line of defense. Never null. Never empty.
 */
object Fallbacks {

    /** Default nudge copy (≤ 15 words) */
    const val NUDGE_COPY = "The person with you deserves your full attention."

    /** Default weekly insight (3–4 sentences) */
    const val WEEKLY_INSIGHT =
        "Your presence data has been collected this week. " +
        "Keep monitoring to unlock personalized insights about your habits. " +
        "Small improvements each day add up to meaningful change."

    /** Default nudge explanation (2–3 sentences) */
    const val NUDGE_EXPLAIN =
        "The app noticed elevated phone activity while someone was nearby. " +
        "High unlock frequency and short sessions triggered this reminder."
}
