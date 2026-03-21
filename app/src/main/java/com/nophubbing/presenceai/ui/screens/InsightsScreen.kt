package com.nophubbing.presenceai.ui.screens

// ── ALL imports explicitly listed — no wildcards that could miss something ──
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nophubbing.presenceai.ai.PresenceHistoryManager
import com.nophubbing.presenceai.ui.components.NudgeAcceptancePieChart
import com.nophubbing.presenceai.ui.components.PresenceLineChart
import com.nophubbing.presenceai.ui.components.ScoreBarChart
import com.nophubbing.presenceai.ui.theme.PresenceColors
import com.nophubbing.presenceai.viewmodel.DashboardViewModel

// ─────────────────────────────────────────────────────────────────────────────
// InsightsScreen — RL bandit + Gemini AI + Historical trends
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun InsightsScreen(vm: DashboardViewModel = viewModel()) {
    val context = LocalContext.current

    // Collect state from ViewModel
    val weeklyInsight by vm.weeklyInsight.collectAsStateWithLifecycle()
    val isLoading     by vm.isLoadingInsight.collectAsStateWithLifecycle()
    val banditStats   by vm.banditStats.collectAsStateWithLifecycle()
    val updateCount   by vm.updateCount.collectAsStateWithLifecycle()
    val presenceScore by vm.presenceScore.collectAsStateWithLifecycle()

    // RL stats
    val hC        = (banditStats["haptic_count"] as? Int)   ?: 0
    val hA        = (banditStats["haptic_avg"]   as? Float) ?: 0f
    val nC        = (banditStats["notif_count"]  as? Int)   ?: 0
    val nA        = (banditStats["notif_avg"]    as? Float) ?: 0f
    val preferred = (banditStats["preferred_arm"] as? String) ?: "HAPTIC"

    // Load history — safe, never throws
    val history = remember(context) {
        try { PresenceHistoryManager.getHistoryList(context) }
        catch (_: Throwable) { emptyList() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        // ── ε-Greedy Bandit ───────────────────────────────────────────────────
        ISLabel("ε-GREEDY BANDIT LEARNING")
        Spacer(Modifier.height(10.dp))
        BanditCard(hC, hA, nC, nA, preferred, updateCount)

        // ── Session ───────────────────────────────────────────────────────────
        Spacer(Modifier.height(20.dp))
        ISLabel("SESSION OVERVIEW")
        Spacer(Modifier.height(10.dp))
        SessionCard(presenceScore, updateCount)

        // ── Gemini AI ─────────────────────────────────────────────────────────
        Spacer(Modifier.height(20.dp))
        ISLabel("GEMINI AI ANALYSIS")
        Spacer(Modifier.height(10.dp))
        GeminiCard(
            insight   = weeklyInsight,
            isLoading = isLoading,
            onRefresh = { vm.generateWeeklyInsight() }
        )

        // ── Historical Trends ─────────────────────────────────────────────────
        if (history.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            ISLabel("HISTORICAL TRENDS")
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(PresenceColors.BgCard)
                    .border(1.dp, PresenceColors.BorderDefault, RoundedCornerShape(20.dp))
                    .padding(18.dp)
            ) {
                Column {
                    PresenceLineChart(history = history, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(28.dp))
                    Row(Modifier.fillMaxWidth()) {
                        ScoreBarChart(history = history, modifier = Modifier.weight(1.2f))
                        Spacer(Modifier.width(16.dp))
                        NudgeAcceptancePieChart(
                            acceptanceRate = history.lastOrNull()?.acceptanceRate ?: 0,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ─── Bandit Card ──────────────────────────────────────────────────────────────
@Composable
private fun BanditCard(hC: Int, hA: Float, nC: Int, nA: Float, preferred: String, cycles: Int) {
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(0f to Color(0xFF071522), 1f to Color(0xFF040D18)))
            .border(1.dp, PresenceColors.AccentCyan.copy(alpha = 0.22f), RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Column {
            // Header row
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text("Nudge Optimizer", color = PresenceColors.TextPrimary,
                        fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("$cycles cycles · ε=20% explore",
                        color = PresenceColors.TextMuted, fontSize = 11.sp)
                }
                PrefBadge(preferred)
            }

            Spacer(Modifier.height(16.dp))

            // Arm cards
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ArmCard("HAPTIC",        Icons.Default.Vibration,     hC, hA,
                    PresenceColors.AccentGreen, preferred == "HAPTIC",        Modifier.weight(1f))
                ArmCard("NOTIFICATION",  Icons.Default.Notifications,  nC, nA,
                    PresenceColors.AccentCyan,  preferred == "NOTIFICATION",  Modifier.weight(1f))
            }

            Spacer(Modifier.height(14.dp))

            val total = hC + nC
            if (total == 0) {
                // Exploration prompt
                Box(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(PresenceColors.AccentAmber.copy(alpha = 0.08f))
                        .border(1.dp, PresenceColors.AccentAmber.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TravelExplore, null,
                            tint = PresenceColors.AccentAmber, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Tap Yes/No on nudge notifications to start learning",
                            color = PresenceColors.AccentAmber, fontSize = 11.sp, lineHeight = 16.sp
                        )
                    }
                }
            } else {
                // Trial split bar
                val hFrac = hC.toFloat() / total
                val af by animateFloatAsState(hFrac, tween(900), label = "bf")

                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("Trial split", color = PresenceColors.TextMuted, fontSize = 10.sp)
                    Text("${(hFrac * 100).toInt()}% haptic · ${(100 - hFrac * 100).toInt()}% notif",
                        color = PresenceColors.TextSecondary, fontSize = 10.sp)
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier.fillMaxWidth().height(7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                ) {
                    if (af > 0f) Box(
                        Modifier.fillMaxWidth(af).matchParentSize()
                            .clip(RoundedCornerShape(4.dp))
                            .background(Brush.horizontalGradient(listOf(
                                PresenceColors.AccentGreen.copy(alpha = 0.7f), PresenceColors.AccentGreen)))
                    )
                    if (af < 1f) Box(
                        Modifier.fillMaxWidth(1f - af).matchParentSize()
                            .align(Alignment.CenterEnd)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Brush.horizontalGradient(listOf(
                                PresenceColors.AccentCyan, PresenceColors.AccentCyan.copy(alpha = 0.7f))))
                    )
                }

                // Winner callout
                val diff = hA - nA
                if (kotlin.math.abs(diff) > 0.01f) {
                    Spacer(Modifier.height(10.dp))
                    val winner = if (diff > 0) "Haptic" else "Notification"
                    val delta  = String.format("%+.2f", if (diff > 0) diff else -diff)
                    val wc     = if (diff > 0) PresenceColors.AccentGreen else PresenceColors.AccentCyan
                    Box(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(wc.copy(alpha = 0.08f))
                            .border(1.dp, wc.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TrendingUp, null, tint = wc, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("$winner leads by $delta avg reward",
                                color = wc, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArmCard(
    label: String, icon: ImageVector,
    count: Int, avg: Float,
    color: Color, winner: Boolean, mod: Modifier
) {
    val bg = if (winner)
        Brush.linearGradient(listOf(color.copy(alpha = 0.14f), color.copy(alpha = 0.05f)))
    else
        Brush.linearGradient(listOf(Color(0xFF0A1520), Color(0xFF060D18)))

    Box(
        mod.clip(RoundedCornerShape(14.dp)).background(bg)
            .border(
                1.dp,
                if (winner) color.copy(alpha = 0.5f) else color.copy(alpha = 0.1f),
                RoundedCornerShape(14.dp)
            ).padding(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text(label, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                if (winner) {
                    Spacer(Modifier.width(3.dp))
                    Icon(Icons.Default.Star, null, tint = color, modifier = Modifier.size(9.dp))
                }
            }
            Spacer(Modifier.height(8.dp))

            // Trial count
            Text("$count", color = PresenceColors.TextPrimary,
                fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp)
            Text("trials", color = PresenceColors.TextMuted, fontSize = 9.sp)
            Spacer(Modifier.height(4.dp))

            // Reward — use String.format("%+.2f") — NO SPACE BEFORE +
            val rc = when { avg > 0.3f -> PresenceColors.AccentGreen; avg < -0.1f -> PresenceColors.AccentCoral; else -> PresenceColors.AccentAmber }
            Text(String.format("%+.2f", avg), color = rc, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text("avg reward", color = PresenceColors.TextMuted, fontSize = 9.sp)
            if (count > 0) {
                Text(String.format("total: %+.2f", avg * count), color = rc.copy(alpha = 0.6f), fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun PrefBadge(pref: String) {
    val c  = if (pref == "HAPTIC") PresenceColors.AccentGreen else PresenceColors.AccentCyan
    val ic = if (pref == "HAPTIC") Icons.Default.Vibration   else Icons.Default.Notifications
    Row(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(c.copy(alpha = 0.12f))
            .border(1.dp, c.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(ic, null, tint = c, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(pref, color = c, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

// ─── Session Card ─────────────────────────────────────────────────────────────
@Composable
private fun SessionCard(score: Float, updates: Int) {
    val sc = when {
        score >= 70 -> PresenceColors.ScoreLow
        score >= 40 -> PresenceColors.ScoreMid
        else        -> PresenceColors.ScoreHigh
    }
    val animScore by animateFloatAsState(score, tween(1000), label = "ps")

    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(0f to Color(0xFF0F0B2A), 1f to Color(0xFF080617)))
            .border(1.dp, PresenceColors.AccentPurple.copy(alpha = 0.18f), RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column {
                Text("Presence Score", color = PresenceColors.TextSecondary, fontSize = 12.sp)
                Text("${animScore.toInt()}", color = sc,
                    fontSize = 50.sp, fontWeight = FontWeight.Black, letterSpacing = (-2).sp)
                Text(
                    when { score >= 70 -> "Excellent presence"; score >= 40 -> "Light drift"; else -> "High distraction" },
                    color = sc, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Loop, null,
                        tint = PresenceColors.AccentPurple, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("$updates learning updates",
                        color = PresenceColors.AccentPurple, fontSize = 12.sp)
                }
            }
            // Mini ring
            MiniRing(score, sc)
        }
    }
}

@Composable
private fun MiniRing(score: Float, color: Color) {
    val anim by animateFloatAsState(score / 100f, tween(1100), label = "mr")
    androidx.compose.foundation.Canvas(Modifier.size(72.dp)) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2 - 6.dp.toPx()
        for (i in 1..3) {
            drawCircle(Color.White.copy(alpha = 0.04f), r * i / 3, c, style = Stroke(1f))
        }
        drawArc(
            Brush.sweepGradient(listOf(color.copy(alpha = 0.3f), color)),
            -90f, anim * 360f, false,
            Offset(c.x - r, c.y - r),
            androidx.compose.ui.geometry.Size(r * 2, r * 2),
            style = Stroke(7.dp.toPx(), cap = StrokeCap.Round)
        )
        drawCircle(color, 3.5.dp.toPx(), c)
    }
}

// ─── Gemini Card ──────────────────────────────────────────────────────────────
@Composable
private fun GeminiCard(insight: String?, isLoading: Boolean, onRefresh: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "gp")
    val pa by pulse.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "gpa"
    )

    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF050210))
            .border(
                1.dp,
                if (isLoading) PresenceColors.AccentPurple.copy(alpha = pa * 0.5f)
                else PresenceColors.AccentPurple.copy(alpha = 0.25f),
                RoundedCornerShape(20.dp)
            ).padding(18.dp)
    ) {
        Column {
            // Header
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(9.dp))
                            .background(
                                Brush.linearGradient(listOf(
                                    PresenceColors.AccentPurple.copy(alpha = 0.28f),
                                    Color(0xFF4285f4).copy(alpha = 0.15f)
                                ))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AutoAwesome, null,
                            tint = PresenceColors.AccentPurple, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(9.dp))
                    Column {
                        Text("Gemini Analysis",
                            color = PresenceColors.AccentPurple, fontSize = 13.sp,
                            fontWeight = FontWeight.Bold)
                        Text("gemini-2.5-flash",
                            color = PresenceColors.TextMuted, fontSize = 9.sp)
                    }
                }
                IconButton(
                    onClick = onRefresh, enabled = !isLoading,
                    modifier = Modifier.size(34.dp).clip(CircleShape)
                        .background(PresenceColors.AccentPurple.copy(alpha = 0.1f))
                ) {
                    if (isLoading)
                        CircularProgressIndicator(color = PresenceColors.AccentPurple,
                            modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else
                        Icon(Icons.Default.Refresh, null,
                            tint = PresenceColors.AccentPurple, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = PresenceColors.AccentPurple.copy(alpha = 0.1f))
            Spacer(Modifier.height(12.dp))

            AnimatedContent(
                targetState = isLoading to insight,
                transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(200)) },
                label = "gc"
            ) { (loading, text) ->
                when {
                    loading -> Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Analyzing your presence patterns…",
                            color = PresenceColors.AccentPurple.copy(alpha = pa),
                            fontSize = 12.sp, fontStyle = FontStyle.Italic,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(14.dp))
                        LinearProgressIndicator(
                            color = PresenceColors.AccentPurple,
                            trackColor = PresenceColors.AccentPurple.copy(alpha = 0.08f),
                            modifier = Modifier.fillMaxWidth(0.55f).clip(RoundedCornerShape(3.dp))
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    text != null -> Text(
                        text, color = PresenceColors.TextPrimary.copy(alpha = 0.9f),
                        fontSize = 13.sp, lineHeight = 21.sp
                    )
                    else -> Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(6.dp))
                        Icon(Icons.Default.AutoAwesome, null,
                            tint = PresenceColors.AccentPurple.copy(alpha = 0.3f),
                            modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Tap ↺ for a personalised Gemini analysis of your week",
                            color = PresenceColors.TextMuted, fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

// ─── Label ────────────────────────────────────────────────────────────────────
@Composable
private fun ISLabel(text: String) = Text(
    text, color = PresenceColors.TextMuted, fontSize = 9.sp,
    fontWeight = FontWeight.Bold, letterSpacing = 2.sp
)
