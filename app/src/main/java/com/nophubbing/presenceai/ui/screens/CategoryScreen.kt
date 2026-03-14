package com.nophubbing.presenceai.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.nophubbing.presenceai.analytics.AppCategoryClassifier
import com.nophubbing.presenceai.analytics.AppCategoryClassifier.Category
import com.nophubbing.presenceai.ui.theme.*

@Composable
fun CategoryScreen(
    breakdown: List<AppCategoryClassifier.CategoryBreakdown>
) {
    val totalMs = breakdown.sumOf { it.totalTimeMs }.coerceAtLeast(1L)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(20.dp))

        if (breakdown.isEmpty()) {
            EmptyState()
        } else {
            // ── Dominant category hero card ───────────────────────────────────
            breakdown.firstOrNull()?.let { top ->
                HeroCard(top, totalMs)
            }

            Spacer(Modifier.height(20.dp))

            // ── Stacked time bar ──────────────────────────────────────────────
            Text("TIME BREAKDOWN", color = TextMuted, fontSize = 10.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.height(10.dp))
            StackedTimeBar(breakdown, totalMs)

            Spacer(Modifier.height(20.dp))

            // ── Per-category cards ────────────────────────────────────────────
            Text("BY CATEGORY", color = TextMuted, fontSize = 10.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.height(10.dp))

            breakdown.forEach { cat ->
                CategoryCard(cat, totalMs)
                Spacer(Modifier.height(10.dp))
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ── Hero card (dominant category) ────────────────────────────────────────────

@Composable
private fun HeroCard(cat: AppCategoryClassifier.CategoryBreakdown, totalMs: Long) {
    val color = categoryColor(cat.category)
    val pct = (cat.totalTimeMs.toFloat() / totalMs * 100).toInt()

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(color.copy(alpha = 0.18f), color.copy(alpha = 0.06f))))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
            .padding(22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Big emoji
            Text(cat.category.emoji, fontSize = 44.sp)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text("MOST TIME SPENT", color = color, fontSize = 9.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(3.dp))
                Text(cat.category.displayName, color = Color.White,
                    fontSize = 20.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(4.dp))
                Text(formatTime(cat.totalTimeMs), color = TextSecondary, fontSize = 13.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("$pct%", color = color, fontSize = 28.sp, fontWeight = FontWeight.Black)
                Text("of window", color = TextMuted, fontSize = 10.sp)
            }
        }
    }
}

// ── Stacked time bar ──────────────────────────────────────────────────────────

@Composable
private fun StackedTimeBar(
    breakdown: List<AppCategoryClassifier.CategoryBreakdown>,
    totalMs: Long
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1A1640))
    ) {
        Row(Modifier.fillMaxSize()) {
            breakdown.forEach { cat ->
                val fraction = cat.totalTimeMs.toFloat() / totalMs
                val animFrac by animateFloatAsState(
                    targetValue = fraction,
                    animationSpec = tween(900, easing = FastOutSlowInEasing),
                    label = "bar_${cat.category.name}"
                )
                if (animFrac > 0.005f) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .weight(animFrac)
                            .background(categoryColor(cat.category).copy(alpha = 0.85f))
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    // Legend dots
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        breakdown.take(5).forEach { cat ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(categoryColor(cat.category), RoundedCornerShape(4.dp))
                )
                Spacer(Modifier.width(4.dp))
                Text(cat.category.displayName, color = TextSecondary, fontSize = 10.sp)
            }
        }
    }
}

// ── Per-category detail card ──────────────────────────────────────────────────

@Composable
private fun CategoryCard(
    cat: AppCategoryClassifier.CategoryBreakdown,
    totalMs: Long
) {
    val color   = categoryColor(cat.category)
    val pct     = cat.totalTimeMs.toFloat() / totalMs
    val animPct by animateFloatAsState(
        targetValue = pct,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "card_${cat.category.name}"
    )

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(BgCard, Color(0xFF0D0A28))))
            .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Column {
            // Header row
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(cat.category.emoji, fontSize = 22.sp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(cat.category.displayName, color = Color.White,
                            fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("${cat.sessionCount} sessions", color = TextMuted, fontSize = 11.sp)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatTime(cat.totalTimeMs), color = color,
                        fontSize = 15.sp, fontWeight = FontWeight.Black)
                    Text("${(pct * 100).toInt()}%", color = TextMuted, fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Progress bar
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF1E1A4A))
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animPct)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Brush.horizontalGradient(listOf(color.copy(0.6f), color)))
                )
            }

            Spacer(Modifier.height(12.dp))

            // Stat pills row
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill("Micro", "${cat.microSessionCount}", color)
                StatPill("Ratio", "${(cat.microRatio * 100).toInt()}%",
                    if (cat.microRatio > 0.5f) PresencePink else PresenceGreen)
                if (cat.packages.isNotEmpty()) {
                    StatPill("Apps", "${cat.packages.size}", PresencePurple)
                }
            }

            // App names (top 3)
            if (cat.packages.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    cat.packages.take(3).joinToString(" · ") { it.substringAfterLast(".") },
                    color = TextMuted, fontSize = 10.sp, lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(4.dp))
        Text(value, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmptyState() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 60.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📊", fontSize = 48.sp)
            Spacer(Modifier.height(16.dp))
            Text("No sessions yet", color = TextSecondary, fontSize = 16.sp,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Use your phone for a few minutes\nthen open the app again.",
                color = TextMuted, fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1_000
    return when {
        totalSec < 60   -> "${totalSec}s"
        totalSec < 3600 -> "${totalSec / 60}m ${totalSec % 60}s"
        else            -> "${totalSec / 3600}h ${(totalSec % 3600) / 60}m"
    }
}

private fun categoryColor(cat: Category): Color = when (cat) {
    Category.SOCIAL_MEDIA   -> Color(0xFFFF007A)   // pink
    Category.ENTERTAINMENT  -> Color(0xFFFF8C42)   // orange
    Category.INFORMATION    -> Color(0xFF00C6FF)   // blue
    Category.COMMUNICATION  -> Color(0xFF00F5A0)   // green
    Category.PRODUCTIVITY   -> Color(0xFFAD7AFF)   // purple
    Category.SHOPPING       -> Color(0xFFFFD166)   // amber
    Category.OTHER          -> Color(0xFF6B6490)   // muted
}
