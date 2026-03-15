package com.nophubbing.presenceai.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nophubbing.presenceai.ui.theme.*
import com.nophubbing.presenceai.viewmodel.DashboardViewModel

@Composable
fun InsightsScreen(vm: DashboardViewModel = viewModel()) {
    val weeklyInsight  by vm.weeklyInsight.collectAsState()
    val isLoading      by vm.isLoadingInsight.collectAsState()
    val banditStats    by vm.banditStats.collectAsState()
    val updateCount    by vm.updateCount.collectAsState()
    val presenceScore  by vm.presenceScore.collectAsState()

    val hCount   = (banditStats["haptic_count"] as? Int)    ?: 0
    val hAvg     = (banditStats["haptic_avg"]   as? Float)  ?: 0f
    val nCount   = (banditStats["notif_count"]  as? Int)    ?: 0
    val nAvg     = (banditStats["notif_avg"]    as? Float)  ?: 0f
    val preferred = (banditStats["preferred_arm"] as? String) ?: "HAPTIC"

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {

        SL("ε-GREEDY BANDIT LEARNING")
        Spacer(Modifier.height(10.dp))
        BanditCard(hCount, hAvg, nCount, nAvg, preferred, updateCount)

        Spacer(Modifier.height(18.dp))
        SL("SESSION OVERVIEW")
        Spacer(Modifier.height(10.dp))
        SessionCard(presenceScore, updateCount)

        Spacer(Modifier.height(18.dp))
        SL("CLAUDE AI ANALYSIS")
        Spacer(Modifier.height(10.dp))
        ClaudeCard(weeklyInsight, isLoading) { vm.generateWeeklyInsight() }

        Spacer(Modifier.height(30.dp))
    }
}

// ─── Bandit Card ──────────────────────────────────────────────────────────────
@Composable
private fun BanditCard(hC: Int, hA: Float, nC: Int, nA: Float, preferred: String, cycles: Int) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
        .background(Brush.linearGradient(0f to Color(0xFF071522), 1f to Color(0xFF040D18)))
        .border(1.dp, PresenceBlue.copy(0.22f), RoundedCornerShape(20.dp)).padding(18.dp)) {
        Column {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text("Nudge Optimizer", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("$cycles learning cycles · ε=20% explore", color = TextMuted, fontSize = 11.sp)
                }
                PBadge(preferred)
            }
            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ArmBox("HAPTIC",        Icons.Default.Vibration,     hC, hA, PresenceGreen, preferred == "HAPTIC",        Modifier.weight(1f))
                ArmBox("NOTIFICATION",  Icons.Default.Notifications,  nC, nA, PresenceBlue,  preferred == "NOTIFICATION",  Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))

            val total = hC + nC
            if (total == 0) {
                // Exploration phase message
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(PresenceOrange.copy(0.08f))
                    .border(1.dp, PresenceOrange.copy(0.25f), RoundedCornerShape(10.dp)).padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TravelExplore, null, tint = PresenceOrange, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Tap Yes/No on nudge notifications to start learning",
                            color = PresenceOrange, fontSize = 11.sp)
                    }
                }
            } else {
                val hFrac = hC.toFloat() / total
                val af by animateFloatAsState(hFrac, tween(900), label = "bf")

                // Split bar
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("Trial split", color = TextMuted, fontSize = 10.sp)
                    Text("${(hFrac*100).toInt()}% haptic · ${(100-hFrac*100).toInt()}% notif",
                        color = TextSecondary, fontSize = 10.sp)
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(0.05f))) {
                    if (af > 0f)
                        Box(Modifier.fillMaxWidth(af).fillMaxHeight().clip(RoundedCornerShape(4.dp))
                            .background(Brush.horizontalGradient(listOf(PresenceGreen.copy(0.7f), PresenceGreen))))
                    if (af < 1f)
                        Box(Modifier.fillMaxWidth(1f - af).fillMaxHeight().align(Alignment.CenterEnd)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Brush.horizontalGradient(listOf(PresenceBlue, PresenceBlue.copy(0.7f)))))
                }

                // Winner callout
                val diff = hA - nA
                if (kotlin.math.abs(diff) > 0.01f) {
                    Spacer(Modifier.height(10.dp))
                    val (winner, delta, wc) =
                        if (diff > 0) Triple("Haptic",       "+${String.format("%.2f", diff)},", PresenceGreen)
                        else          Triple("Notification", "+${String.format("%.2f", -diff)}", PresenceBlue)
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(wc.copy(0.08f)).border(1.dp, wc.copy(0.2f), RoundedCornerShape(8.dp)).padding(10.dp)) {
                        Text("$winner leads by $delta avg reward", color = wc,
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ArmBox(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int, avg: Float, color: Color, winner: Boolean, mod: Modifier) {
    val bg = if (winner) Brush.linearGradient(listOf(color.copy(0.14f), color.copy(0.05f)))
             else Brush.linearGradient(listOf(Color(0xFF0A1520), Color(0xFF060D18)))
    Box(mod.clip(RoundedCornerShape(14.dp)).background(bg)
        .border(1.dp, if (winner) color.copy(0.5f) else color.copy(0.1f), RoundedCornerShape(14.dp)).padding(14.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text(label, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                if (winner) { Spacer(Modifier.width(3.dp)); Icon(Icons.Default.Star, null, tint = color, modifier = Modifier.size(9.dp)) }
            }
            Spacer(Modifier.height(8.dp))
            Text("$count", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp)
            Text("trials", color = TextMuted, fontSize = 9.sp)
            Spacer(Modifier.height(4.dp))
            val rc = when { avg > 0.3f -> PresenceGreen; avg < -0.1f -> PresencePink; else -> PresenceOrange }
            // Show reward prominently — this is the key metric
            Text(String.format("%+.2f", avg), color = rc, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text("avg reward", color = TextMuted, fontSize = 9.sp)
            // Show total reward too
            if (count > 0) {
                Text(String.format("total: %+.2f", avg * count), color = rc.copy(0.6f), fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun PBadge(preferred: String) {
    val c  = if (preferred == "HAPTIC") PresenceGreen else PresenceBlue
    val ic = if (preferred == "HAPTIC") Icons.Default.Vibration else Icons.Default.Notifications
    Row(Modifier.clip(RoundedCornerShape(20.dp)).background(c.copy(0.12f))
        .border(1.dp, c.copy(0.35f), RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(ic, null, tint = c, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(preferred, color = c, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

// ─── Session Card ─────────────────────────────────────────────────────────────
@Composable
private fun SessionCard(presenceScore: Float, updates: Int) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
        .background(Brush.linearGradient(0f to Color(0xFF0F0B2A), 1f to Color(0xFF080617)))
        .border(1.dp, PresencePurple.copy(0.18f), RoundedCornerShape(20.dp)).padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column {
                Text("Presence Score", color = TextSecondary, fontSize = 12.sp)
                val sc = cL(RiskLow, RiskHigh, 1f - presenceScore / 100f)
                val as_ by animateFloatAsState(presenceScore, tween(1000), label = "ps")
                Text("${as_.toInt()}", color = sc, fontSize = 50.sp, fontWeight = FontWeight.Black, letterSpacing = (-2).sp)
                Text(when {
                    presenceScore >= 80 -> "Excellent presence"
                    presenceScore >= 60 -> "Light drift"
                    presenceScore >= 40 -> "Drifting"
                    else -> "High distraction"
                }, color = sc, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Loop, null, tint = PresencePurple, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("$updates learning updates", color = PresencePurple, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
            MiniRing(presenceScore)
        }
    }
}

@Composable
private fun MiniRing(score: Float) {
    val color = cL(RiskLow, RiskHigh, 1f - score / 100f)
    val anim by animateFloatAsState(score / 100f, tween(1100), label = "mr")
    androidx.compose.foundation.Canvas(Modifier.size(72.dp)) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2 - 6.dp.toPx()
        repeat(3) { i -> drawCircle(Color.White.copy(0.04f), r * (i + 1) / 3, c, style = Stroke(1f)) }
        drawArc(Brush.sweepGradient(listOf(color.copy(0.3f), color)), -90f, anim * 360f, false,
            Offset(c.x - r, c.y - r), androidx.compose.ui.geometry.Size(r * 2, r * 2),
            style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(color, 3.5.dp.toPx(), c)
    }
}

// ─── Claude Card ──────────────────────────────────────────────────────────────
@Composable
private fun ClaudeCard(insight: String?, isLoading: Boolean, onRefresh: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "p")
    val pa by pulse.animateFloat(0.4f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "pa")
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF050210))
        .border(1.dp, if (isLoading) PresencePurple.copy(pa * 0.5f) else PresencePurple.copy(0.25f), RoundedCornerShape(20.dp)).padding(18.dp)) {
        Column {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp))
                        .background(Brush.linearGradient(listOf(PresencePurple.copy(0.28f), PresencePink.copy(0.12f)))),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AutoAwesome, null, tint = PresencePurple, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(9.dp))
                    Column {
                        Text("Claude Analysis", color = PresencePurple, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("claude-sonnet-4-20250514", color = TextMuted, fontSize = 9.sp)
                    }
                }
                IconButton(onClick = onRefresh, enabled = !isLoading,
                    modifier = Modifier.size(34.dp).clip(CircleShape).background(PresencePurple.copy(0.1f))) {
                    if (isLoading)
                        CircularProgressIndicator(color = PresencePurple, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else
                        Icon(Icons.Default.Refresh, null, tint = PresencePurple, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = PresencePurple.copy(0.1f))
            Spacer(Modifier.height(12.dp))
            AnimatedContent(isLoading to insight,
                transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(200)) }, label = "cc") { (loading, text) ->
                when {
                    loading -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(6.dp))
                        Text("Analyzing your presence patterns…", color = PresencePurple.copy(pa),
                            fontSize = 12.sp, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(14.dp))
                        LinearProgressIndicator(color = PresencePurple, trackColor = PresencePurple.copy(0.08f),
                            modifier = Modifier.fillMaxWidth(0.55f).clip(RoundedCornerShape(3.dp)))
                        Spacer(Modifier.height(6.dp))
                    }
                    text != null -> Text(text, color = Color.White.copy(0.88f), fontSize = 13.sp, lineHeight = 21.sp)
                    else -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(6.dp))
                        Icon(Icons.Default.AutoAwesome, null, tint = PresencePurple.copy(0.3f), modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(6.dp))
                        Text("Tap ↺ for a personalized Claude analysis", color = TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable private fun SL(t: String) = Text(t, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)

private fun cL(a: Color, b: Color, t: Float): Color {
    val tc = t.coerceIn(0f, 1f)
    return Color(a.red+(b.red-a.red)*tc, a.green+(b.green-a.green)*tc, a.blue+(b.blue-a.blue)*tc, 1f)
}
