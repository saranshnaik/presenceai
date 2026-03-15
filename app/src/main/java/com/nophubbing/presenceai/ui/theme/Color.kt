package com.nophubbing.presenceai.ui.theme

import androidx.compose.ui.graphics.Color

// ── New Presence Colors ──────────────────────────────────────────────────────

object PresenceColors {
    // Backgrounds
    val BgDeep       = Color(0xFF0B0B1A)
    val BgCard       = Color(0xFF0F0F24)
    val BgElevated   = Color(0xFF16163A)
    val BgNudge      = Color(0xFF1A0D2E)
    val BgInsight    = Color(0xFF0D1A18)

    // Borders
    val BorderDefault = Color(0xFF1E1E3A)
    val BorderNudge   = Color(0xFF3B1F6A)
    val BorderInsight = Color(0xFF163A32)

    // Score states (ring + state label)
    val ScoreHigh    = Color(0xFFfb923c)   // > 65% distracted
    val ScoreMid     = Color(0xFFfbbf24)   // 35–65%
    val ScoreLow     = Color(0xFF34d399)   // < 35% distracted (user is present)

    // Text
    val TextPrimary   = Color(0xFFf0eef8)
    val TextSecondary = Color(0xFF9d9bb8)
    val TextMuted     = Color(0xFF5a5878)
    val TextDim       = Color(0xFF3a3860)

    // Accents
    val AccentPurple = Color(0xFFa78bfa)
    val AccentCyan   = Color(0xFF22d3ee)
    val AccentAmber  = Color(0xFFfbbf24)
    val AccentGreen  = Color(0xFF34d399)
    val AccentCoral  = Color(0xFFfb923c)
}

// ── Legacy palette (kept so existing screens like CategoryScreen still compile) ──

val PresenceBlue     = Color(0xFF00C6FF)
val PresencePink     = Color(0xFFFF007A)
val PresencePurple   = Color(0xFFAD7AFF)
val PresenceGreen    = Color(0xFF00F5A0)
val PresenceOrange   = Color(0xFFFF8C42)

val BgDeep           = Color(0xFF080616)
val BgMid            = Color(0xFF0F0C29)
val BgCard           = Color(0xFF17143A)
val BgCardBorder     = Color(0xFF2A2660)

val TextPrimary      = Color(0xFFFFFFFF)
val TextSecondary    = Color(0xFFB0A8D4)
val TextMuted        = Color(0xFF6B6490)

val RiskLow          = Color(0xFF00F5A0)
val RiskMid          = Color(0xFFFFD166)
val RiskHigh         = Color(0xFFFF007A)
