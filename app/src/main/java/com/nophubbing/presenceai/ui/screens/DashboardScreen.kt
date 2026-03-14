package com.nophubbing.presenceai.ui.screens

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nophubbing.presenceai.ml.FEATURE_NAMES
import com.nophubbing.presenceai.analytics.AppCategoryClassifier
import com.nophubbing.presenceai.rl.NudgeFormat
import com.nophubbing.presenceai.services.MonitoringService
import com.nophubbing.presenceai.services.MonitoringState
import com.nophubbing.presenceai.ui.components.*
import com.nophubbing.presenceai.ui.theme.*
import com.nophubbing.presenceai.viewmodel.DashboardViewModel

@Composable
fun DashboardScreen(viewModel: DashboardViewModel = viewModel()) {
    val signals       by viewModel.signals.collectAsState()
    val pPhub         by viewModel.pPhub.collectAsState()
    val pDrift        by viewModel.pDrift.collectAsState()
    val presenceScore by viewModel.presenceScore.collectAsState()
    val shouldNudge   by viewModel.shouldNudge.collectAsState()
    val nudgeFormat   by viewModel.nudgeFormat.collectAsState()
    val accuracy      by viewModel.accuracy.collectAsState()
    val updateCount   by viewModel.updateCount.collectAsState()
    val banditStats   by viewModel.banditStats.collectAsState()
    val featureVals   by viewModel.featureValues.collectAsState()
    val categoryData  by viewModel.categoryBreakdown.collectAsState()
    // Live tick counter — increments every 1s to show the score is updating
    val tickMs = remember { kotlinx.coroutines.flow.MutableStateFlow(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { kotlinx.coroutines.delay(1_000L); tickMs.value = System.currentTimeMillis() }
    }
    val lastUpdated by tickMs.collectAsState()
    val isRunning     by MonitoringState.isRunning.collectAsState()
    val context       = LocalContext.current

    var selectedTab by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(BgDeep, BgMid, Color(0xFF0A0820)))
        )
    ) {
        Column(Modifier.fillMaxSize()) {
            TopBar(isRunning) {
                val intent = Intent(context, MonitoringService::class.java)
                if (!isRunning) context.startForegroundService(intent)
                else context.stopService(intent)
            }

            PresenceTabRow(
                tabs = listOf("Dashboard", "Categories", "Insights", "Signals", "Debug"),
                selected = selectedTab,
                onSelect = { selectedTab = it }
            )

            when (selectedTab) {
                0 -> MainDashboard(
                    presenceScore = presenceScore, pPhub = pPhub, pDrift = pDrift,
                    accuracy = accuracy, updateCount = updateCount,
                    shouldNudge = shouldNudge, nudgeFormat = nudgeFormat,
                    unlocks = signals?.unlocks ?: 0,
                    sessions = signals?.totalSessions ?: 0,
                    microSessions = signals?.microSessions ?: 0,
                    notifReflex = signals?.notificationReflexCount ?: 0,
                    banditStats = banditStats,
                    isRunning = isRunning,
                    voiceLevel = viewModel.voiceLevel.collectAsState().value,
                    proximityStrength = viewModel.proximityStrength.collectAsState().value
                )
                1 -> CategoryScreen(breakdown = categoryData)
                2 -> InsightsScreen()
                3 -> SignalsTab(featureVals = featureVals, pDrift = pDrift, pPhub = pPhub)
                4 -> DebugTab(
                    pDrift = pDrift, pPhub = pPhub, presenceScore = presenceScore,
                    updateCount = updateCount, accuracy = accuracy,
                    banditStats = banditStats, featureVals = featureVals, isRunning = isRunning
                )
            }
        }
    }
}

// ─── Top Bar ─────────────────────────────────────────────────────────────────

@Composable
private fun TopBar(isRunning: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Presence AI", fontSize = 28.sp, fontWeight = FontWeight.Black,
                color = Color.White, letterSpacing = (-0.5).sp)
            Text(
                if (isRunning) "Observing gracefully" else "Monitoring paused",
                color = if (isRunning) TextSecondary else TextMuted, fontSize = 13.sp
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (isRunning) Brush.horizontalGradient(listOf(Color(0xFF003D2F), Color(0xFF005F48)))
                    else           Brush.horizontalGradient(listOf(Color(0xFF3D0020), Color(0xFF5F0035)))
                )
                .clickable(onClick = onToggle)
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(
                    if (isRunning) PresenceGreen else PresencePink, RoundedCornerShape(4.dp)
                ))
                Spacer(Modifier.width(8.dp))
                Text(if (isRunning) "ON" else "OFF",
                    color = if (isRunning) PresenceGreen else PresencePink,
                    fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

// ─── Tab Row ─────────────────────────────────────────────────────────────────

@Composable
private fun PresenceTabRow(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF100D2A))
            .padding(4.dp)
            .fillMaxWidth()
    ) {
        tabs.forEachIndexed { idx, tab ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (idx == selected) BgCard else Color.Transparent)
                    .clickable { onSelect(idx) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(tab, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = if (idx == selected) Color.White else TextMuted)
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

// ─── Dashboard Tab ────────────────────────────────────────────────────────────

@Composable
private fun MainDashboard(
    presenceScore: Float, pPhub: Float, pDrift: Float,
    accuracy: Float, updateCount: Int,
    shouldNudge: Boolean, nudgeFormat: NudgeFormat?,
    unlocks: Int, sessions: Int, microSessions: Int, notifReflex: Int,
    banditStats: Map<String, Any>,
    isRunning: Boolean,
    voiceLevel: Float,
    proximityStrength: Float
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            PresenceCircle(presenceScore = presenceScore, pPhub = pPhub)
        }
        // Live indicator dot
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(6.dp).background(
                if (isRunning) PresenceGreen else TextMuted, RoundedCornerShape(3.dp)
            ))
            Spacer(Modifier.width(6.dp))
            Text(
                if (isRunning) "Live — updating every second" else "Monitoring paused",
                color = if (isRunning) PresenceGreen else TextMuted,
                fontSize = 10.sp, fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(16.dp))

        NudgeAlert(visible = shouldNudge, nudgeFormat = nudgeFormat,
            nudgeText = nudgeText(pPhub, unlocks))
        if (shouldNudge) Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("MODEL ACCURACY", "${(accuracy * 100).toInt()}%",
                "$updateCount updates", PresenceGreen, Modifier.weight(1f))
            MetricCard("DRIFT SCORE", String.format("%.2f", pDrift),
                "P(drift)", PresenceOrange, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("UNLOCKS", unlocks.toString(), "Last 10 min", PresenceBlue, Modifier.weight(1f))
            MetricCard("SESSIONS", sessions.toString(), "App switches", PresencePurple, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("MICRO SESS", microSessions.toString(), "Under 20s", PresencePink, Modifier.weight(1f))
            MetricCard("NOTIF REFLEX", if (notifReflex > 0) "YES" else "NO",
                "Quick unlock",
                if (notifReflex > 0) PresencePink else PresenceGreen, Modifier.weight(1f))
        }

        Spacer(Modifier.height(20.dp))
        RealTimeSensorRow(voiceLevel, proximityStrength)

        Spacer(Modifier.height(20.dp))
        InsightCard(insightText(pPhub, presenceScore, unlocks, microSessions))
        Spacer(Modifier.height(16.dp))
        if (banditStats.isNotEmpty()) BanditStatsCard(banditStats)
        Spacer(Modifier.height(32.dp))
    }
}

// ─── Signals Tab ──────────────────────────────────────────────────────────────

@Composable
private fun SignalsTab(featureVals: FloatArray, pDrift: Float, pPhub: Float) {
    // Display names for the 14 features
    val shortLabels = listOf("x0","x1","x2","x3","x4","x5","x6","x7","x8","x9","x10","x11","x12","x13")
    val featureColors = listOf(
        PresenceBlue, PresenceGreen, PresencePurple, PresenceOrange, PresenceBlue,
        PresencePink, PresenceOrange, PresenceBlue, PresencePink, PresenceGreen,
        PresenceGreen, PresenceBlue, PresenceGreen, PresenceBlue
    )
    // Normalised display values — already [0,1] after FeatureEngineering, except x8 in [-1,1]
    val normVals = FloatArray(featureVals.size) { i ->
        if (i == 8) (featureVals[i] + 1f) / 2f else featureVals[i].coerceIn(0f, 1f)
    }
    val rawLabels = featureVals.mapIndexed { i, v ->
        when (i) {
            10, 11 -> if (v > 0.5f) "YES" else "NO"
            else   -> String.format("%.2f", v)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)
    ) {
        Text("LIVE SIGNAL FEATURES (14-feature model)", color = TextMuted,
            fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(16.dp))

        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(BgCard, Color(0xFF0D0A28))))
                .border(1.dp, BgCardBorder, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column {
                featureVals.indices.forEach { i ->
                    FeatureBar(
                        label = FEATURE_NAMES[i], shortLabel = shortLabels[i],
                        value = normVals[i], rawValue = rawLabels[i],
                        color = featureColors[i]
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("PIPELINE OUTPUT", color = TextMuted,
            fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PipelineOutputCard("P(DRIFT)", String.format("%.3f", pDrift), PresenceOrange, Modifier.weight(1f))
            PipelineOutputCard("P(PHUB)",  String.format("%.3f", pPhub),
                if (pPhub > 0.65f) PresencePink else PresenceGreen, Modifier.weight(1f))
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun PipelineOutputCard(title: String, value: String, color: Color, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(16.dp))
        .background(Brush.linearGradient(listOf(BgCard, Color(0xFF0D0A28))))
        .border(1.dp, color.copy(0.3f), RoundedCornerShape(16.dp))
        .padding(16.dp)
    ) {
        Column {
            Text(title, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(6.dp))
            Text(value, color = color, fontSize = 28.sp, fontWeight = FontWeight.Black)
        }
    }
}

// ─── Debug Tab ───────────────────────────────────────────────────────────────

@Composable
private fun DebugTab(
    pDrift: Float, pPhub: Float, presenceScore: Float,
    updateCount: Int, accuracy: Float,
    banditStats: Map<String, Any>, featureVals: FloatArray, isRunning: Boolean
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("DEBUG CONSOLE", color = TextMuted, fontSize = 10.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(12.dp))

        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF060412))
                .border(1.dp, Color(0xFF1A1640), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column {
                DLine("SERVICE",      if (isRunning) "RUNNING" else "STOPPED", if (isRunning) PresenceGreen else PresencePink)
                DLine("P(DRIFT)",     String.format("%.4f", pDrift), PresenceOrange)
                DLine("P(PHUB)",      String.format("%.4f", pPhub), if (pPhub > 0.65f) PresencePink else PresenceGreen)
                DLine("SCORE",        String.format("%.1f", presenceScore), PresenceBlue)
                DLine("LR UPDATES",   updateCount.toString(), PresencePurple)
                DLine("ACCURACY",     "${(accuracy * 100).toInt()}%", PresenceGreen)
                DSep()
                featureVals.forEachIndexed { i, v ->
                    DLine(FEATURE_NAMES[i], String.format("%.4f", v), TextSecondary)
                }
                DSep()
                DLine("HAPTIC COUNT", (banditStats["haptic_count"] as? Int)?.toString() ?: "0", PresenceGreen)
                DLine("HAPTIC AVG",   String.format("%.3f", (banditStats["haptic_avg"] as? Float) ?: 0f), PresenceGreen)
                DLine("NOTIF COUNT",  (banditStats["notif_count"] as? Int)?.toString() ?: "0", PresenceBlue)
                DLine("NOTIF AVG",    String.format("%.3f", (banditStats["notif_avg"] as? Float) ?: 0f), PresenceBlue)
                DLine("PREFERRED",    (banditStats["preferred_arm"] as? String) ?: "—", PresencePurple)
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun DLine(key: String, value: String, valueColor: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), Arrangement.SpaceBetween) {
        Text("» $key", color = Color(0xFF4A4870), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = valueColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun DSep() = HorizontalDivider(color = Color(0xFF1A1640), modifier = Modifier.padding(vertical = 6.dp))

@Composable
private fun RealTimeSensorRow(voiceLevel: Float, proximityStrength: Float) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BgCard)
            .border(1.dp, BgCardBorder, RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Text("LIVE SENSOR SIGNALS", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(16.dp))
        SensorBar(label = "Voice Activity", value = voiceLevel / 100f, color = PresenceGreen)
        Spacer(Modifier.height(12.dp))
        SensorBar(label = "Nearby Proximity", value = proximityStrength / 100f, color = PresenceBlue)
    }
}

@Composable
private fun SensorBar(label: String, value: Float, color: Color) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Text("${(value * 100).toInt()}%", color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(0.05f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(value.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(Brush.horizontalGradient(listOf(color.copy(0.7f), color)))
            )
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private fun nudgeText(pPhub: Float, unlocks: Int) = when {
    unlocks >= 8  -> "You've unlocked $unlocks times recently. The conversation here is worth more."
    pPhub > 0.8f  -> "Your attention is drifting. The person with you deserves your full presence."
    else          -> "The person with you deserves your full attention."
}

private fun insightText(pPhub: Float, score: Float, unlocks: Int, micro: Int) = when {
    score >= 85 -> "Great presence — low phone engagement detected. Keep it up!"
    unlocks > 6 && micro > 3 ->
        "You've unlocked $unlocks times with $micro micro-sessions. Try keeping the phone face-down."
    pPhub > 0.6f -> "Elevated phone engagement detected. Try the phone-face-down technique."
    else -> "Moderate phone activity. Awareness is the first step to change."
}
