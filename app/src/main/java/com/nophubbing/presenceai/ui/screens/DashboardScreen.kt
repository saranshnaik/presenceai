package com.nophubbing.presenceai.ui.screens

import android.content.Intent
import androidx.compose.animation.*        // covers animateFloatAsState, AnimatedVisibility, etc.
import androidx.compose.animation.core.*   // covers tween, FastOutSlowInEasing, RepeatMode, etc.
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nophubbing.presenceai.ml.PipelineConfig
import com.nophubbing.presenceai.rl.NudgeFormat
import com.nophubbing.presenceai.services.MonitoringService
import com.nophubbing.presenceai.services.MonitoringState
import com.nophubbing.presenceai.ui.components.*
import com.nophubbing.presenceai.ui.theme.*
import com.nophubbing.presenceai.viewmodel.DashboardViewModel

@Composable
fun DashboardScreen(vm: DashboardViewModel = viewModel()) {
    val signals        by vm.signals.collectAsState()
    val pPhub          by vm.pPhub.collectAsState()
    val pDrift         by vm.pDrift.collectAsState()
    val presenceScore  by vm.presenceScore.collectAsState()
    val shouldNudge    by vm.shouldNudge.collectAsState()
    val nudgeFormat    by vm.nudgeFormat.collectAsState()
    val nudgeCopy      by vm.nudgeCopy.collectAsState()
    val nudgeExplain   by vm.nudgeExplanation.collectAsState()
    val loadingExplain by vm.isLoadingExplain.collectAsState()
    val updateCount    by vm.updateCount.collectAsState()
    val banditStats    by vm.banditStats.collectAsState()
    val featureVals    by vm.featureValues.collectAsState()
    val voiceLevel     by vm.voiceLevel.collectAsState()
    val proximityStr   by vm.proximityStrength.collectAsState()
    val isRunning      by MonitoringState.isRunning.collectAsState()
    val context        = LocalContext.current
    var tab            by remember { mutableStateOf(0) }

    Box(Modifier.fillMaxSize().background(
        Brush.verticalGradient(listOf(Color(0xFF050210), Color(0xFF090620), Color(0xFF060414)))
    )) {
        Column(Modifier.fillMaxSize()) {
            TopBar(isRunning) {
                val i = Intent(context, MonitoringService::class.java)
                if (!isRunning) context.startForegroundService(i) else context.stopService(i)
            }
            TabRow(listOf("Live", "Insights", "Signals", "Debug"), tab) { tab = it }
            when (tab) {
                0 -> LiveTab(
                    presenceScore, pPhub, pDrift, updateCount,
                    shouldNudge, nudgeFormat, nudgeCopy, nudgeExplain, loadingExplain,
                    onWhy = { vm.explainLastNudge() },
                    onDismissExplain = { vm.dismissNudgeExplanation() },
                    unlocks  = signals?.unlocks ?: 0,
                    sessions = signals?.totalSessions ?: 0,
                    micro    = signals?.microSessions ?: 0,
                    reflex   = signals?.notificationReflexCount ?: 0,
                    banditStats, isRunning, voiceLevel, proximityStr
                )
                1 -> InsightsScreen(vm)
                2 -> SignalsTab(featureVals, pDrift, pPhub)
                3 -> DebugTab(pDrift, pPhub, presenceScore, updateCount, banditStats, featureVals, isRunning)
            }
        }
    }
}

@Composable
private fun TopBar(isRunning: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Column {
            Text("Presence AI", fontSize = 24.sp, fontWeight = FontWeight.Black,
                color = Color.White, letterSpacing = (-0.5).sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(if (isRunning) PresenceGreen else TextMuted, CircleShape))
                Spacer(Modifier.width(5.dp))
                Text(if (isRunning) "Observing gracefully" else "Monitoring paused",
                    color = if (isRunning) PresenceGreen else TextMuted, fontSize = 12.sp)
            }
        }
        Box(Modifier.clip(RoundedCornerShape(12.dp)).background(
            if (isRunning) Brush.horizontalGradient(listOf(Color(0xFF003D2F), Color(0xFF00603E)))
            else Brush.horizontalGradient(listOf(Color(0xFF3D001A), Color(0xFF5F0030)))
        ).clickable(onClick = onToggle).padding(horizontal = 20.dp, vertical = 10.dp)) {
            Text(if (isRunning) "ON" else "OFF",
                color = if (isRunning) PresenceGreen else PresencePink,
                fontWeight = FontWeight.Black, fontSize = 14.sp)
        }
    }
}

@Composable
private fun TabRow(tabs: List<String>, sel: Int, onSel: (Int) -> Unit) {
    Row(Modifier.padding(horizontal = 20.dp).clip(RoundedCornerShape(12.dp))
        .background(Color(0xFF0E0B24)).padding(3.dp).fillMaxWidth()) {
        tabs.forEachIndexed { i, t ->
            Box(Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                .background(if (i == sel) BgCard else Color.Transparent)
                .clickable { onSel(i) }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center) {
                Text(t, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = if (i == sel) Color.White else TextMuted)
            }
        }
    }
    Spacer(Modifier.height(6.dp))
}

// ─── Live Tab ─────────────────────────────────────────────────────────────────
@Composable
private fun LiveTab(
    presenceScore: Float, pPhub: Float, pDrift: Float, updateCount: Int,
    shouldNudge: Boolean, nudgeFormat: NudgeFormat?,
    nudgeCopy: String, nudgeExplain: String?, loadingExplain: Boolean,
    onWhy: () -> Unit, onDismissExplain: () -> Unit,
    unlocks: Int, sessions: Int, micro: Int, reflex: Int,
    banditStats: Map<String, Any>, isRunning: Boolean,
    voiceLevel: Float, proximityStrength: Float
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(16.dp))

        // ── Presence ring ──────────────────────────────────────────────────
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PresenceCircle(presenceScore = presenceScore, pPhub = pPhub)
        }
        Row(Modifier.fillMaxWidth(), Arrangement.Center, Alignment.CenterVertically) {
            Box(Modifier.size(5.dp).background(if (isRunning) PresenceGreen else TextMuted, CircleShape))
            Spacer(Modifier.width(5.dp))
            Text(if (isRunning) "Live · updates every second" else "Paused",
                color = if (isRunning) PresenceGreen else TextMuted, fontSize = 10.sp)
        }
        Spacer(Modifier.height(16.dp))

        // ── Nudge alert ────────────────────────────────────────────────────
        NudgeAlertV2(shouldNudge, nudgeFormat, nudgeCopy, nudgeExplain, loadingExplain, onWhy, onDismissExplain)
        if (shouldNudge) Spacer(Modifier.height(14.dp))

        // ── Metrics (no accuracy) ──────────────────────────────────────────
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("P(PHUB)", String.format("%.2f", pPhub), "Phubbing score",
                if (pPhub > 0.65f) PresencePink else if (pPhub > 0.4f) PresenceOrange else PresenceGreen,
                Modifier.weight(1f))
            MetricCard("UPDATES", updateCount.toString(), "Learning cycles", PresencePurple, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("UNLOCKS",  unlocks.toString(),  "Last 10 min",  PresenceBlue,   Modifier.weight(1f))
            MetricCard("SESSIONS", sessions.toString(), "App switches", PresencePurple, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("MICRO",  micro.toString(),                  "< 20s",      PresencePink,  Modifier.weight(1f))
            MetricCard("REFLEX", if (reflex > 0) "YES" else "NO",  "Notif unlock",
                if (reflex > 0) PresencePink else PresenceGreen, Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))

        SensorCard(voiceLevel, proximityStrength)
        Spacer(Modifier.height(14.dp))
        InsightCard(insightText(pPhub, presenceScore, unlocks, micro))
        Spacer(Modifier.height(14.dp))
        if (banditStats.isNotEmpty()) BanditStatsCard(banditStats)
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun SensorCard(voice: Float, prox: Float) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
        .background(BgCard).border(1.dp, BgCardBorder, RoundedCornerShape(18.dp)).padding(16.dp)) {
        Column {
            Text("LIVE SENSORS", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(12.dp))
            SBar("Voice Activity", voice / 100f, PresenceGreen)
            Spacer(Modifier.height(8.dp))
            SBar("Proximity",      prox / 100f,  PresenceBlue)
        }
    }
}

@Composable
private fun SBar(label: String, v: Float, color: Color) {
    val av by animateFloatAsState(v.coerceIn(0f, 1f), tween(500), label = "sb")
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary, fontSize = 11.sp)
        Text("${(av * 100).toInt()}%", color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(4.dp))
    Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(0.05f))) {
        Box(Modifier.fillMaxWidth(av).fillMaxHeight().clip(RoundedCornerShape(3.dp))
            .background(Brush.horizontalGradient(listOf(color.copy(0.6f), color))))
    }
}

// ─── Signals Tab ──────────────────────────────────────────────────────────────
@Composable
private fun SignalsTab(fv: FloatArray, pDrift: Float, pPhub: Float) {
    val names  = PipelineConfig.FEATURE_NAMES
    val colors = listOf(PresenceBlue, PresenceGreen, PresencePurple, PresenceOrange, PresenceBlue, PresencePink, PresenceOrange)
    val vals   = if (fv.size == 7) fv else FloatArray(7)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("SIGNAL FEATURES (7)", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(BgCard, Color(0xFF0D0A28))))
            .border(1.dp, BgCardBorder, RoundedCornerShape(18.dp)).padding(16.dp)) {
            Column {
                vals.forEachIndexed { i, v ->
                    FeatureBar(names.getOrElse(i){"x$i"}, "x${i+1}",
                        v.coerceIn(0f,1f), String.format("%.2f", v), colors.getOrElse(i){PresenceBlue})
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("PIPELINE OUTPUT", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            POut("P(DRIFT)", String.format("%.3f", pDrift), PresenceOrange, Modifier.weight(1f))
            POut("P(PHUB)",  String.format("%.3f", pPhub),
                if (pPhub > 0.65f) PresencePink else PresenceGreen, Modifier.weight(1f))
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable private fun POut(t: String, v: String, c: Color, m: Modifier) {
    Box(m.clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(BgCard, Color(0xFF0D0A28))))
        .border(1.dp, c.copy(0.3f), RoundedCornerShape(14.dp)).padding(14.dp)) {
        Column {
            Text(t, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(4.dp))
            Text(v, color = c, fontSize = 26.sp, fontWeight = FontWeight.Black)
        }
    }
}

// ─── Debug Tab ────────────────────────────────────────────────────────────────
@Composable
private fun DebugTab(
    pDrift: Float, pPhub: Float, presenceScore: Float, updateCount: Int,
    banditStats: Map<String, Any>, fv: FloatArray, isRunning: Boolean
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("DEBUG CONSOLE", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF040210))
            .border(1.dp, Color(0xFF181540), RoundedCornerShape(14.dp)).padding(14.dp)) {
            Column {
                DL("SERVICE",     if (isRunning) "RUNNING" else "STOPPED", if (isRunning) PresenceGreen else PresencePink)
                DL("P(DRIFT)",    String.format("%.4f", pDrift), PresenceOrange)
                DL("P(PHUB)",     String.format("%.4f", pPhub), if (pPhub > 0.65f) PresencePink else PresenceGreen)
                DL("SCORE",       String.format("%.1f", presenceScore), PresenceBlue)
                DL("LR UPDATES",  updateCount.toString(), PresencePurple)
                DS()
                val names = PipelineConfig.FEATURE_NAMES
                val vals  = if (fv.size == 7) fv else FloatArray(7)
                vals.forEachIndexed { i, v -> DL(names.getOrElse(i){"x$i"}, String.format("%.4f", v), TextSecondary) }
                DS()
                DL("HAPTIC COUNT",  (banditStats["haptic_count"] as? Int)?.toString() ?: "0", PresenceGreen)
                DL("HAPTIC AVG",    String.format("%.3f", (banditStats["haptic_avg"] as? Float) ?: 0f), PresenceGreen)
                DL("HAPTIC TOTAL",  String.format("%.3f",
                    ((banditStats["haptic_count"] as? Int) ?: 0).let { n ->
                        if (n > 0) ((banditStats["haptic_avg"] as? Float) ?: 0f) * n else 0f }), PresenceGreen)
                DL("NOTIF COUNT",   (banditStats["notif_count"] as? Int)?.toString() ?: "0", PresenceBlue)
                DL("NOTIF AVG",     String.format("%.3f", (banditStats["notif_avg"] as? Float) ?: 0f), PresenceBlue)
                DL("PREFERRED",     (banditStats["preferred_arm"] as? String) ?: "—", PresencePurple)
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable private fun DL(k: String, v: String, vc: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), Arrangement.SpaceBetween) {
        Text("» $k", color = Color(0xFF3E3C6A), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Text(v, color = vc, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}
@Composable private fun DS() = HorizontalDivider(color = Color(0xFF181540), modifier = Modifier.padding(vertical = 5.dp))

private fun insightText(pPhub: Float, score: Float, unlocks: Int, micro: Int) = when {
    score >= 85 -> "Great presence — low phone engagement right now."
    unlocks > 6 && micro > 3 -> "You've unlocked $unlocks times with $micro micro-sessions. Try placing the phone face-down."
    pPhub > 0.6f -> "Elevated phone engagement. A moment of distance goes a long way."
    else -> "Moderate activity. Awareness is the first step."
}
