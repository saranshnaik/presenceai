package com.nophubbing.presenceai.ui.screens

import android.content.Intent
import androidx.compose.animation.*        // covers animateFloatAsState, AnimatedVisibility, etc.
import androidx.compose.animation.core.*   // covers tween, FastOutSlowInEasing, RepeatMode, etc.
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
<<<<<<< HEAD
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
=======
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Vibration
>>>>>>> 812d93c9763a4dcbb68f4ea9d5819da4db7407fb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
<<<<<<< HEAD
import com.nophubbing.presenceai.ml.PipelineConfig
=======
import com.nophubbing.presenceai.analytics.AppCategoryClassifier
import com.nophubbing.presenceai.rl.BanditState
>>>>>>> 812d93c9763a4dcbb68f4ea9d5819da4db7407fb
import com.nophubbing.presenceai.rl.NudgeFormat
import com.nophubbing.presenceai.services.MonitoringService
import com.nophubbing.presenceai.services.MonitoringState
import com.nophubbing.presenceai.ui.components.*
import com.nophubbing.presenceai.ui.theme.*
import com.nophubbing.presenceai.viewmodel.DashboardViewModel

@Composable
<<<<<<< HEAD
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
=======
fun DashboardScreen(viewModel: DashboardViewModel = viewModel()) {
    val signals       by viewModel.signals.collectAsState()
    val pPhub         by viewModel.pPhub.collectAsState()
    val shouldNudge   by viewModel.shouldNudge.collectAsState()
    val nudgeFormat   by viewModel.nudgeFormat.collectAsState()
    val banditStats   by viewModel.banditStats.collectAsState()
    val categoryData  by viewModel.categoryBreakdown.collectAsState()
    val isRunning     by MonitoringState.isRunning.collectAsState()
    val context       = LocalContext.current

    val presenceScore = remember(pPhub) { pPhubToPresenceScore(pPhub) }
    val state         = remember(presenceScore) { presenceScoreToState(presenceScore) }

    var selectedTab by remember { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize().background(
        Brush.verticalGradient(listOf(Color(0xFF050210), Color(0xFF090620), Color(0xFF060414)))
    )) {
        Column(Modifier.fillMaxSize()) {
            Header(isRunning) {
                val i = Intent(context, MonitoringService::class.java)
                if (!isRunning) context.startForegroundService(i) else context.stopService(i)
            }
            TabBar(listOf("Live", "Categories", "Insights", "Signals", "Debug"), tab) { tab = it }
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
                    bleNearby = (signals?.peopleNearbyCount ?: 0) > 0,
                    banditStats, isRunning, voiceLevel, proximityStr
                )
                1 -> CategoryScreen(breakdown = categoryData)
                2 -> InsightsScreen()
                3 -> SettingsTab()
>>>>>>> 812d93c9763a4dcbb68f4ea9d5819da4db7407fb
            }
        }
    }
}

<<<<<<< HEAD
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
=======
// ─────────────────────────────────────────────────────────────────────────────
// HEADER
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun Header(isRunning: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Column {
            Text(
                "Presence AI",
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = PresenceColors.TextPrimary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subLabel,
                fontSize = 13.sp,
                color = PresenceColors.TextMuted
            )
        }
        MonitoringToggle(isOn = isMonitoring, onClick = onToggle)
>>>>>>> 812d93c9763a4dcbb68f4ea9d5819da4db7407fb
    }
}

@Composable
<<<<<<< HEAD
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
=======
private fun MonitoringToggle(isOn: Boolean, onClick: () -> Unit) {
    val bgColor  = if (isOn) Color(0xFF0d2a1a) else Color(0xFF1a1a1a)
    val dotColor = if (isOn) PresenceColors.AccentGreen else Color(0xFF555555)
    val txtColor = if (isOn) PresenceColors.AccentGreen else PresenceColors.TextMuted
    val label    = if (isOn) "ON" else "OFF"

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .border(
                1.dp,
                if (isOn) Color(0xFF1a4a2e) else Color(0xFF2a2a2a),
                RoundedCornerShape(20.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = txtColor)
    }
}

// ─── Tab Bar ─────────────────────────────────────────────────────────────────
@Composable
private fun TabBar(tabs: List<String>, sel: Int, onSel: (Int) -> Unit) {
    Row(Modifier.padding(horizontal = 20.dp).clip(RoundedCornerShape(12.dp))
        .background(Color(0xFF0E0B24)).padding(3.dp).fillMaxWidth()) {
        tabs.forEachIndexed { i, t ->
            Box(Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                .background(if (i == sel) BgCard else Color.Transparent)
                .clickable { onSel(i) }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center) {
                Text(t, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    color = if (i == sel) PresenceColors.TextPrimary else PresenceColors.TextMuted)
            }
        }
    }
    Spacer(Modifier.height(6.dp))
}

// ─── LIVE TAB ─────────────────────────────────────────────────────────────────
@Composable
private fun LiveTab(
    presenceScore: Float, pPhub: Float, pDrift: Float, updateCount: Int,
    shouldNudge: Boolean, nudgeFormat: NudgeFormat?,
    nudgeCopy: String, nudgeExplain: String?, loadingExplain: Boolean,
    onWhy: () -> Unit, onDismissExplain: () -> Unit,
    unlocks: Int, sessions: Int, micro: Int, reflex: Int,
    bleNearby: Boolean,
    banditStats: Map<String, Any>,
    isRunning: Boolean,
    voiceLevel: Float, proximityStrength: Float
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(8.dp))

        // Score Ring
        ScoreRing(presenceScore, pPhub, isRunning)

        Spacer(Modifier.height(14.dp))

        // Nudge alert (Gemini-powered copy)
        NudgeAlertV2(shouldNudge, nudgeFormat, nudgeCopy, nudgeExplain, loadingExplain,
            onWhy, onDismissExplain, Modifier.padding(horizontal = 16.dp))
        if (shouldNudge) Spacer(Modifier.height(12.dp))

        // Context cards
        SL("SOCIAL CONTEXT")
        ContextRow(bleNearby, unlocks, micro)
        Spacer(Modifier.height(14.dp))

        SL("LIVE SENSORS")
        SensorRow(voiceLevel, proximityStrength)
        Spacer(Modifier.height(14.dp))

        // Activity metrics
        SL("ACTIVITY")
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CCard("UPDATES",  updateCount.toString(), "Learning",     PresenceColors.AccentPurple, Modifier.weight(1f))
            CCard("SESSIONS", sessions.toString(),    "App switches", PresenceColors.AccentCyan,   Modifier.weight(1f))
            CCard("REFLEX",   if (reflex > 0) "YES" else "NO", "Notif unlock",
                if (reflex > 0) PresenceColors.AccentCoral else PresenceColors.AccentGreen, Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))

        // AI Insight
        InsightCard(insightText(pPhub, presenceScore, unlocks, micro))
        Spacer(Modifier.height(14.dp))

        // RL Bandit
        if (banditStats.isNotEmpty()) {
            SL("NUDGE LEARNING (RL)")
            BanditLearningCard(banditStats)
        }
        Spacer(Modifier.height(30.dp))
    }
}

// ─── Score Ring ───────────────────────────────────────────────────────────────
@Composable
private fun ScoreRing(presenceScore: Float, pPhub: Float, isLive: Boolean) {
    val scoreColor = when {
        presenceScore >= 70 -> PresenceColors.ScoreLow
        presenceScore >= 40 -> PresenceColors.ScoreMid
        else                -> PresenceColors.ScoreHigh
    }
    val animScore by animateFloatAsState(presenceScore, tween(1000), label = "sc")
    val animSweep by animateFloatAsState(pPhub * 360f, tween(1000), label = "sw")

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {

            // Glow pulse when score is high risk
            if (pPhub > 0.4f) {
                val pulse = rememberInfiniteTransition(label = "rp")
                val pa by pulse.animateFloat(0.04f, if (pPhub > 0.65f) 0.20f else 0.10f,
                    infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pa")
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(scoreColor.copy(pa), size.minDimension / 2f - 20f)
                }
            }

            Canvas(Modifier.fillMaxSize().padding(14.dp)) {
                val sw = 16.dp.toPx(); val r = (size.minDimension - sw) / 2f
                val tl = Offset(sw / 2, sw / 2); val sz = Size(r * 2, r * 2)
                drawArc(Color(0xFF16163A), 0f, 360f, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
                drawArc(Brush.sweepGradient(listOf(scoreColor.copy(0.5f), scoreColor, scoreColor.copy(0.5f))),
                    -90f, animSweep, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
                if (animSweep > 5f) {
                    val rad = Math.toRadians((-90.0 + animSweep)).toFloat()
                    val cx  = size.width / 2 + r * kotlin.math.cos(rad)
                    val cy  = size.height / 2 + r * kotlin.math.sin(rad)
                    drawCircle(scoreColor, sw / 2, Offset(cx, cy))
                    drawCircle(Color.White.copy(0.9f), sw / 4, Offset(cx, cy))
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${animScore.toInt()}", fontSize = 54.sp, fontWeight = FontWeight.Black,
                    color = PresenceColors.TextPrimary, letterSpacing = (-2).sp)
                Text("PRESENCE", color = PresenceColors.TextSecondary, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                Spacer(Modifier.height(2.dp))
                Text(when {
                    presenceScore >= 70 -> "Fully Present"
                    presenceScore >= 40 -> "Slightly Distracted"
                    else                -> "High Distraction"
                }, color = scoreColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // Live indicator
        if (isLive) {
            val pulse = rememberInfiniteTransition(label = "lp")
            val a by pulse.animateFloat(0.5f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "la")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(5.dp).background(PresenceColors.AccentGreen.copy(a), CircleShape))
                Spacer(Modifier.width(5.dp))
                Text("Live · updates every second", color = PresenceColors.AccentGreen, fontSize = 10.sp)
            }
        } else {
            Text(
                "Monitoring paused",
                fontSize = 11.sp,
                color = PresenceColors.TextDim
            )
>>>>>>> 812d93c9763a4dcbb68f4ea9d5819da4db7407fb
        }
    }
}

<<<<<<< HEAD
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
=======
// ─────────────────────────────────────────────────────────────────────────────
// NUDGE CARD
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ContextRow(bleNearby: Boolean, unlocks: Int, micro: Int) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CCard("NEARBY",  if (bleNearby) "Yes" else "No",
            if (bleNearby) "BT confirmed" else "None detected",
            if (bleNearby) PresenceColors.AccentCyan else PresenceColors.TextMuted, Modifier.weight(1f))
        CCard("UNLOCKS", "$unlocks", "Last 10 min",
            if (unlocks > 3) PresenceColors.AccentAmber else PresenceColors.AccentGreen, Modifier.weight(1f))
        CCard("QUICK",   "$micro",   "< 20s sessions",
            if (micro > 2) PresenceColors.AccentCoral else PresenceColors.AccentGreen, Modifier.weight(1f))
    }
}

@Composable
private fun CCard(label: String, value: String, sub: String, color: Color, mod: Modifier) {
    Column(mod.clip(RoundedCornerShape(12.dp)).background(PresenceColors.BgCard)
        .border(1.dp, PresenceColors.BorderDefault, RoundedCornerShape(12.dp)).padding(10.dp)) {
        Text(label, fontSize = 8.sp, letterSpacing = 1.sp, color = PresenceColors.TextDim, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Black, color = color, letterSpacing = (-0.5).sp)
        Text(sub, fontSize = 9.sp, color = PresenceColors.TextSecondary, lineHeight = 12.sp)
    }
}

// ─── Sensor Row ───────────────────────────────────────────────────────────────
@Composable
private fun SensorRow(voice: Float, prox: Float) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SCard("VOICE",     "${voice.toInt()}%",    voice / 100f,  PresenceColors.AccentGreen, Modifier.weight(1f))
        SCard("PROXIMITY", "${prox.toInt()}%",     prox / 100f,   PresenceColors.AccentCyan,  Modifier.weight(1f))
    }
}

@Composable
private fun SCard(label: String, value: String, frac: Float, color: Color, mod: Modifier) {
    val af by animateFloatAsState(frac.coerceIn(0f, 1f), tween(700), label = "sf$label")
    Column(mod.clip(RoundedCornerShape(12.dp)).background(PresenceColors.BgCard)
        .border(1.dp, PresenceColors.BorderDefault, RoundedCornerShape(12.dp)).padding(10.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(label, fontSize = 8.sp, letterSpacing = 1.sp, color = PresenceColors.TextDim, fontWeight = FontWeight.Bold)
            Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(0.05f))) {
            Box(Modifier.fillMaxWidth(af).fillMaxHeight().clip(RoundedCornerShape(2.dp))
                .background(Brush.horizontalGradient(listOf(color.copy(0.6f), color))))
        }
    }
}

// ─── Insight Card ─────────────────────────────────────────────────────────────
@Composable
private fun InsightCard(text: String) {
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
        .background(PresenceColors.BgInsight)
        .border(1.dp, PresenceColors.BorderInsight, RoundedCornerShape(14.dp)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, null, tint = PresenceColors.AccentGreen,
                modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
            Text("AI INSIGHT", fontSize = 9.sp, letterSpacing = 1.sp,
                color = PresenceColors.AccentGreen, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Text(text, fontSize = 13.sp, color = Color(0xFF9de0ce), lineHeight = 20.sp)
    }
}

// ─── Bandit Learning Card ─────────────────────────────────────────────────────
@Composable
private fun BanditLearningCard(stats: Map<String, Any>) {
    val hC = (stats["haptic_count"] as? Int)   ?: 0
    val hA = (stats["haptic_avg"]   as? Float) ?: 0f
    val nC = (stats["notif_count"]  as? Int)   ?: 0
    val nA = (stats["notif_avg"]    as? Float) ?: 0f
    val preferred = (stats["preferred_arm"] as? String) ?: "HAPTIC"

    val caption = when {
        hC < 5 || nC < 5 -> "Tap Yes/No on nudge notifications to teach me your preferences"
        hA > nA           -> "You respond ${"%.1f".format(hA / nA.coerceAtLeast(0.01f))}× better to vibrations"
        else              -> "You respond better to notifications"
    }

    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth()
        .clip(RoundedCornerShape(14.dp)).background(PresenceColors.BgCard)
        .border(1.dp, PresenceColors.BorderDefault, RoundedCornerShape(14.dp)).padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("RL NUDGE OPTIMIZER", fontSize = 9.sp, letterSpacing = 1.5.sp,
                color = PresenceColors.TextDim, fontWeight = FontWeight.Bold)
            val pc = if (preferred == "HAPTIC") PresenceColors.AccentGreen else PresenceColors.AccentCyan
            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(pc.copy(0.12f))
                .border(1.dp, pc.copy(0.3f), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(preferred, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = pc)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BBar("Haptic",        hC, hA, PresenceColors.AccentGreen, Modifier.weight(1f))
            BBar("Notification",  nC, nA, PresenceColors.AccentCyan,  Modifier.weight(1f))
        }
        HorizontalDivider(color = PresenceColors.BorderDefault, modifier = Modifier.padding(vertical = 10.dp))
        Text(caption, fontSize = 11.sp, color = PresenceColors.TextSecondary, lineHeight = 17.sp)
    }
}

@Composable
private fun BBar(label: String, count: Int, avg: Float, color: Color, mod: Modifier) {
    val frac = ((avg + 1f) / 2f).coerceIn(0.04f, 1f)
    val af by animateFloatAsState(frac, tween(900), label = "bb$label")
    val rc  = when { avg > 0.3f -> PresenceColors.AccentGreen; avg < -0.1f -> PresenceColors.AccentCoral; else -> PresenceColors.AccentAmber }
    Column(mod) {
        Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.BottomStart) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(af).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                .background(color.copy(0.22f)))
            Box(Modifier.fillMaxWidth().fillMaxHeight(af * 0.35f).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                .background(color.copy(0.45f)))
        }
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (label == "Haptic") Icons.Default.Vibration else Icons.Default.Notifications,
                null, tint = color, modifier = Modifier.size(11.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, fontSize = 10.sp, color = PresenceColors.TextSecondary)
        }
        Text("$count trials", fontSize = 10.sp, color = PresenceColors.TextMuted)
        Text("${"%+.2f".format(avg)}", fontSize = 14.sp, fontWeight = FontWeight.Black, color = rc)
        if (count > 0) Text("total: ${"%+.2f".format(avg * count)}", fontSize = 9.sp, color = rc.copy(0.6f))
    }
}

// ─── Signals Tab ──────────────────────────────────────────────────────────────
@Composable
private fun SignalsTab(fv: FloatArray, pDrift: Float, pPhub: Float) {
    val names  = PipelineConfig.FEATURE_NAMES
    val colors = listOf(PresenceColors.AccentCyan, PresenceColors.AccentGreen, PresenceColors.AccentPurple,
        PresenceColors.AccentAmber, PresenceColors.AccentCyan, PresenceColors.AccentCoral, PresenceColors.AccentAmber)
    val vals   = if (fv.size == 7) fv else FloatArray(7)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("SIGNAL FEATURES (7)", color = PresenceColors.TextMuted, fontSize = 9.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(PresenceColors.BgCard)
            .border(1.dp, PresenceColors.BorderDefault, RoundedCornerShape(18.dp)).padding(16.dp)) {
            Column { vals.forEachIndexed { i, v ->
                FeatureBar(names.getOrElse(i){"x$i"}, "x${i+1}",
                    v.coerceIn(0f,1f), "%.2f".format(v), colors.getOrElse(i){PresenceColors.AccentCyan})
            }}
        }
        Spacer(Modifier.height(16.dp))
        Text("PIPELINE OUTPUT", color = PresenceColors.TextMuted, fontSize = 9.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            POut("P(DRIFT)", "%.3f".format(pDrift), PresenceColors.AccentAmber, Modifier.weight(1f))
            POut("P(PHUB)",  "%.3f".format(pPhub),
                if (pPhub > 0.65f) PresenceColors.AccentCoral else PresenceColors.AccentGreen, Modifier.weight(1f))
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun POut(t: String, v: String, c: Color, m: Modifier) {
    Box(m.clip(RoundedCornerShape(14.dp)).background(PresenceColors.BgCard)
        .border(1.dp, c.copy(0.3f), RoundedCornerShape(14.dp)).padding(14.dp)) {
        Column {
            Text(t, color = PresenceColors.TextMuted, fontSize = 9.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
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
        Text("DEBUG CONSOLE", color = PresenceColors.TextMuted, fontSize = 9.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF040210)).border(1.dp, Color(0xFF181540), RoundedCornerShape(14.dp)).padding(14.dp)) {
            Column {
                DL("SERVICE",    if (isRunning) "RUNNING" else "STOPPED",
                    if (isRunning) PresenceColors.AccentGreen else PresencePink)
                DL("P(DRIFT)",   "%.4f".format(pDrift), PresenceColors.AccentAmber)
                DL("P(PHUB)",    "%.4f".format(pPhub),
                    if (pPhub > 0.65f) PresenceColors.AccentCoral else PresenceColors.AccentGreen)
                DL("SCORE",      "%.1f".format(presenceScore),     PresenceColors.AccentCyan)
                DL("LR UPDATES", updateCount.toString(),            PresenceColors.AccentPurple)
                DS()
                val names = PipelineConfig.FEATURE_NAMES
                val vals  = if (fv.size == 7) fv else FloatArray(7)
                vals.forEachIndexed { i, v -> DL(names.getOrElse(i){"x$i"}, "%.4f".format(v), PresenceColors.TextSecondary) }
                DS()
                DL("HAPTIC COUNT", (banditStats["haptic_count"] as? Int)?.toString() ?: "0",  PresenceColors.AccentGreen)
                DL("HAPTIC AVG",   "%.3f".format((banditStats["haptic_avg"] as? Float) ?: 0f), PresenceColors.AccentGreen)
                DL("NOTIF COUNT",  (banditStats["notif_count"] as? Int)?.toString() ?: "0",   PresenceColors.AccentCyan)
                DL("NOTIF AVG",    "%.3f".format((banditStats["notif_avg"] as? Float) ?: 0f),  PresenceColors.AccentCyan)
                DL("PREFERRED",    (banditStats["preferred_arm"] as? String) ?: "—",           PresenceColors.AccentPurple)
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
@Composable private fun SL(text: String) =
    Text(text, color = PresenceColors.TextDim, fontSize = 9.sp, fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp, modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp))

private fun insightText(pPhub: Float, score: Int, unlocks: Int, micro: Int) = when {
    score >= 85 -> "Great presence — low phone engagement detected. Keep it up!"
    unlocks > 6 && micro > 3 ->
        "You've unlocked $unlocks times with $micro quick checks. Try keeping the phone face-down."
    pPhub > 0.6f -> "Elevated phone engagement detected. Try the phone-face-down technique."
    else -> "Moderate phone activity. Awareness is the first step to change."
>>>>>>> 812d93c9763a4dcbb68f4ea9d5819da4db7407fb
}
