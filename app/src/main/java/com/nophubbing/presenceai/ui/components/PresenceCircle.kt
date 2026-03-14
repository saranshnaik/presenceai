package com.nophubbing.presenceai.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nophubbing.presenceai.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun PresenceCircle(
    presenceScore: Float,   // 0–100  (higher = more present)
    pPhub: Float,           // 0–1    (raw phubbing probability)
    modifier: Modifier = Modifier
) {
    val animScore by animateFloatAsState(
        targetValue = presenceScore,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "score"
    )
    val animPhub by animateFloatAsState(
        targetValue = pPhub,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "phub"
    )

    // Pulse animation when phubbing detected
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulseAnim.animateFloat(
        initialValue = 0.2f, targetValue = if (pPhub > 0.65f) 0.7f else 0.2f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    // Colour interpolation: green → amber → pink
    val arcColor = lerp(
        lerp(RiskLow, RiskMid, (animPhub * 2f).coerceIn(0f, 1f)),
        RiskHigh,
        ((animPhub - 0.5f) * 2f).coerceIn(0f, 1f)
    )

    Box(modifier = modifier.size(260.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeW = 18.dp.toPx()
            val glowW   = 32.dp.toPx()
            val radius  = (size.minDimension - glowW) / 2f
            val topLeft = Offset((size.width - radius * 2) / 2, (size.height - radius * 2) / 2)
            val arcSize = Size(radius * 2, radius * 2)
            val sweep   = animPhub * 360f

            // Outer glow ring (pulsing when alerting)
            if (pPhub > 0.4f) {
                drawArc(
                    color = arcColor.copy(alpha = pulseAlpha * 0.3f),
                    startAngle = -90f, sweepAngle = sweep, useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = Stroke(width = glowW, cap = StrokeCap.Round)
                )
            }

            // Background track
            drawArc(
                color = Color(0xFF1E1A4A),
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )

            // Foreground arc — phubbing probability
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(arcColor.copy(alpha = 0.6f), arcColor, arcColor.copy(alpha = 0.6f))
                ),
                startAngle = -90f, sweepAngle = sweep, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )

            // Dot at arc tip
            if (sweep > 5f) {
                val angleRad = Math.toRadians((-90.0 + sweep)).toFloat()
                val cx = size.width / 2 + radius * cos(angleRad)
                val cy = size.height / 2 + radius * sin(angleRad)
                drawCircle(color = arcColor, radius = strokeW / 2, center = Offset(cx, cy))
                drawCircle(color = Color.White.copy(alpha = 0.9f), radius = strokeW / 4, center = Offset(cx, cy))
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${animScore.toInt()}",
                fontSize = 64.sp, fontWeight = FontWeight.Black, color = Color.White,
                letterSpacing = (-2).sp
            )
            Text("PRESENCE", color = TextSecondary, fontSize = 11.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Spacer(Modifier.height(2.dp))
            Text(
                text = when {
                    animScore >= 80 -> "Fully Present"
                    animScore >= 60 -> "Light Drift"
                    animScore >= 40 -> "Drifting"
                    else            -> "High Distraction"
                },
                color = lerp(RiskLow, RiskHigh, (1f - animScore / 100f)),
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun lerp(a: Color, b: Color, t: Float): Color {
    val tc = t.coerceIn(0f, 1f)
    return Color(
        red   = a.red   + (b.red   - a.red)   * tc,
        green = a.green + (b.green - a.green) * tc,
        blue  = a.blue  + (b.blue  - a.blue)  * tc,
        alpha = 1f
    )
}
