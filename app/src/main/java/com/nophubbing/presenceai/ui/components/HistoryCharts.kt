package com.nophubbing.presenceai.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nophubbing.presenceai.ai.WeeklyHistory
import com.nophubbing.presenceai.ui.theme.*

@Composable
fun PresenceLineChart(history: List<WeeklyHistory>, modifier: Modifier = Modifier) {
    if (history.isEmpty()) return

    Column(modifier = modifier) {
        Text(
            "SCORE PROGRESSION",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = PresenceColors.TextMuted,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(12.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .padding(horizontal = 8.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val maxScore = 100f
                val points = history.map { it.avgScore.toFloat() }
                
                if (points.size < 2) return@Canvas

                val stepX = width / (points.size - 1)
                val path = Path()
                
                points.forEachIndexed { index, score ->
                    val x = index * stepX
                    val y = height - (score / maxScore * height)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }

                // Draw gradient area
                val fillPath = Path().apply {
                    addPath(path)
                    lineTo(width, height)
                    lineTo(0f, height)
                    close()
                }
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(PresenceColors.AccentPurple.copy(alpha = 0.3f), Color.Transparent)
                    )
                )

                // Draw line
                drawPath(
                    path = path,
                    color = PresenceColors.AccentPurple,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Draw points
                points.forEachIndexed { index, score ->
                    val x = index * stepX
                    val y = height - (score / maxScore * height)
                    drawCircle(
                        color = Color.White,
                        radius = 4.dp.toPx(),
                        center = Offset(x, y)
                    )
                    drawCircle(
                        color = PresenceColors.AccentPurple,
                        radius = 2.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }
        }
    }
}

@Composable
fun ScoreBarChart(history: List<WeeklyHistory>, modifier: Modifier = Modifier) {
    if (history.isEmpty()) return

    Column(modifier = modifier) {
        Text(
            "PEAK VS AVERAGE",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = PresenceColors.TextMuted,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.Bottom
        ) {
            history.takeLast(4).forEach { week ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.BottomCenter) {
                        // Peak Score Bar
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .height((week.bestScore * 0.8).dp)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(PresenceColors.AccentPurple.copy(alpha = 0.4f))
                        )
                        // Avg Score Bar
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .height((week.avgScore * 0.8).dp)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(PresenceColors.AccentPurple)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        week.weekLabel.split("-").first().trim(),
                        fontSize = 9.sp,
                        color = PresenceColors.TextMuted
                    )
                }
            }
        }
    }
}

@Composable
fun NudgeAcceptancePieChart(acceptanceRate: Int, modifier: Modifier = Modifier) {
    val animatedProgress by animateFloatAsState(
        targetValue = acceptanceRate.toFloat(),
        animationSpec = tween(1000),
        label = "pie"
    )

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 10.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2
                
                // Track
                drawArc(
                    color = Color.White.copy(alpha = 0.1f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(strokeWidth)
                )
                
                // Progress
                drawArc(
                    brush = Brush.sweepGradient(
                        0f to PresenceColors.AccentCyan,
                        0.5f to PresenceColors.AccentPurple,
                        1f to PresenceColors.AccentCyan
                    ),
                    startAngle = -90f,
                    sweepAngle = animatedProgress * 3.6f,
                    useCenter = false,
                    style = Stroke(strokeWidth, cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${acceptanceRate}%",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    "ACCEPT",
                    fontSize = 8.sp,
                    color = PresenceColors.TextMuted,
                    letterSpacing = 1.sp
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "NUDGE RESPONSIVENESS",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = PresenceColors.TextMuted,
            letterSpacing = 1.sp
        )
    }
}
