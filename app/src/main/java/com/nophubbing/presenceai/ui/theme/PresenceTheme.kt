package com.nophubbing.presenceai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary          = PresenceColors.AccentCyan,
    secondary        = PresenceColors.AccentGreen,
    background       = PresenceColors.BgDeep,
    surface          = PresenceColors.BgCard,
    onPrimary        = Color.Black,
    onSecondary      = Color.Black,
    onBackground     = PresenceColors.TextPrimary,
    onSurface        = PresenceColors.TextPrimary,
    error            = PresenceColors.AccentRed,
    onError          = Color.White
)

@Composable
fun PresenceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content     = content
    )
}
