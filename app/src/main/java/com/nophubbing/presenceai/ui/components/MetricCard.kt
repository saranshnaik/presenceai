package com.nophubbing.presenceai.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nophubbing.presenceai.ui.theme.*

@Composable
fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    accentColor: Color = PresenceBlue,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(BgCard, Color(0xFF120F30))
                )
            )
            .border(1.dp, BgCardBorder, RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Column {
            Text(title, color = TextMuted, fontSize = 10.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(10.dp))
            Text(value, fontWeight = FontWeight.Black, fontSize = 30.sp, color = accentColor)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = TextSecondary, fontSize = 11.sp)
        }
        // Accent dot
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(accentColor, RoundedCornerShape(3.dp))
                .align(Alignment.TopEnd)
        )
    }
}

@Composable
fun FeatureBar(
    label: String,
    shortLabel: String,
    value: Float,       // 0–1 normalised for display
    rawValue: String,
    color: Color = PresenceBlue
) {
    val animVal by animateFloatAsState(
        targetValue = value.coerceIn(0f, 1f),
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "bar"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
    ) {
        Text(shortLabel, color = TextMuted, fontSize = 10.sp,
            fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF1E1A4A))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animVal)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), color))
                    )
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(rawValue, color = color, fontSize = 11.sp,
            fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp))
    }
}

@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
