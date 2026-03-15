package com.nophubbing.presenceai.ui.screens

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nophubbing.presenceai.analytics.AppCategoryClassifier
import com.nophubbing.presenceai.rl.BanditState
import com.nophubbing.presenceai.rl.NudgeFormat
import com.nophubbing.presenceai.services.MonitoringService
import com.nophubbing.presenceai.services.MonitoringState
import com.nophubbing.presenceai.ui.theme.PresenceColors
import com.nophubbing.presenceai.utils.PresenceState
import com.nophubbing.presenceai.utils.pPhubToPresenceScore
import com.nophubbing.presenceai.utils.presenceScoreToState
import com.nophubbing.presenceai.viewmodel.DashboardViewModel

// ─────────────────────────────────────────────────────────────────────────────
// ROOT SCREEN — composes all tabs
// ─────────────────────────────────────────────────────────────────────────────

@Composable
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PresenceColors.BgDeep)
    ) {
        Column(Modifier.fillMaxSize()) {
            // ── Header ────────────────────────────────────────────────────
            PresenceHeader(
                isMonitoring = isRunning,
                subLabel     = state.subLabel,
                onToggle     = {
                    val intent = Intent(context, MonitoringService::class.java)
                    if (!isRunning) context.startForegroundService(intent)
                    else context.stopService(intent)
                }
            )

            // ── Tab bar (4 tabs — Today, Patterns, Insights, Settings) ──
            PresenceTabBar(
                selected = selectedTab,
                onSelect = { selectedTab = it }
            )

            when (selectedTab) {
                0 -> TodayTab(
                    presenceScore = presenceScore,
                    state         = state,
                    isLive        = isRunning,
                    shouldNudge   = shouldNudge,
                    nudgeFormat   = nudgeFormat,
                    pPhub         = pPhub,
                    unlocks       = signals?.unlocks ?: 0,
                    bleNearby     = (signals?.peopleNearbyCount ?: 0) > 0,
                    unlockCount   = signals?.unlocks ?: 0,
                    quickChecks   = signals?.microSessions ?: 0,
                    banditStats   = banditStats,
                    voiceLevel    = viewModel.voiceLevel.collectAsState().value,
                    proximityStrength = viewModel.proximityStrength.collectAsState().value
                )
                1 -> CategoryScreen(breakdown = categoryData)
                2 -> InsightsScreen()
                3 -> SettingsTab()
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HEADER
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PresenceHeader(
    isMonitoring: Boolean,
    subLabel: String,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
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
    }
}

@Composable
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

// ─────────────────────────────────────────────────────────────────────────────
// TAB BAR (4 tabs)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PresenceTabBar(selected: Int, onSelect: (Int) -> Unit) {
    val tabs = listOf("Today", "Patterns", "Insights", "Settings")

    LazyRow(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(tabs) { index, tab ->
            val isActive = selected == index
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isActive) Color(0xFF18163a) else Color.Transparent)
                    .border(
                        1.dp,
                        if (isActive) Color(0xFF2a2560) else Color.Transparent,
                        RoundedCornerShape(20.dp)
                    )
                    .clickable { onSelect(index) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    tab,
                    fontSize = 13.sp,
                    fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                    color = if (isActive) PresenceColors.AccentPurple
                            else PresenceColors.TextMuted
                )
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}

// ─────────────────────────────────────────────────────────────────────────────
// TODAY TAB (main dashboard)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TodayTab(
    presenceScore: Int,
    state: PresenceState,
    isLive: Boolean,
    shouldNudge: Boolean,
    nudgeFormat: NudgeFormat?,
    pPhub: Float,
    unlocks: Int,
    bleNearby: Boolean,
    unlockCount: Int,
    quickChecks: Int,
    banditStats: Map<String, Any>,
    voiceLevel: Float,
    proximityStrength: Float
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        // ── Score ring ────────────────────────────────────────────────
        ScoreRing(
            score  = presenceScore,
            state  = state,
            isLive = isLive
        )

        Spacer(Modifier.height(16.dp))

        // ── Nudge card (only when active) ─────────────────────────────
        AnimatedVisibility(
            visible = shouldNudge,
            enter   = fadeIn() + expandVertically(expandFrom = Alignment.Top),
            exit    = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
        ) {
            Column {
                NudgeCard(
                    copy   = nudgeText(pPhub, unlocks),
                    format = nudgeFormat?.name ?: "HAPTIC"
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        // ── Social context row ────────────────────────────────────────
        SectionLabel("SOCIAL CONTEXT")
        ContextRow(
            bleNearby    = bleNearby,
            unlockCount  = unlockCount,
            quickChecks  = quickChecks
        )

        Spacer(Modifier.height(16.dp))

        // ── Live sensor overview ──────────────────────────────────────
        SectionLabel("LIVE SENSORS")
        SensorRow(voiceLevel = voiceLevel, proximityStrength = proximityStrength)

        Spacer(Modifier.height(16.dp))

        // ── AI insight ────────────────────────────────────────────────
        InsightCard(
            text = insightText(pPhub, presenceScore, unlocks, quickChecks)
        )

        Spacer(Modifier.height(16.dp))

        // ── Nudge learning ────────────────────────────────────────────
        if (banditStats.isNotEmpty()) {
            SectionLabel("HOW YOU RESPOND TO NUDGES")
            BanditLearningCard(banditStats = banditStats)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SCORE RING
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ScoreRing(score: Int, state: PresenceState, isLive: Boolean) {
    val animatedScore by animateIntAsState(
        targetValue = score,
        animationSpec = tween(1000, easing = EaseOut),
        label = "score"
    )
    val animatedSweep by animateFloatAsState(
        targetValue = score * 2.4f,     // 100 points = 240° sweep
        animationSpec = tween(1000, easing = EaseOut),
        label = "sweep"
    )
    val ringColor = state.ringColor

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(220.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                val strokeWidth = 14.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2
                val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                val arcSize = Size(radius * 2, radius * 2)

                // Track
                drawArc(
                    color      = Color(0xFF16163A),
                    startAngle = -210f,
                    sweepAngle = 240f,
                    useCenter  = false,
                    topLeft    = topLeft,
                    size       = arcSize,
                    style      = Stroke(strokeWidth, cap = StrokeCap.Round)
                )
                // Fill
                drawArc(
                    color      = ringColor,
                    startAngle = -210f,
                    sweepAngle = animatedSweep,
                    useCenter  = false,
                    topLeft    = topLeft,
                    size       = arcSize,
                    style      = Stroke(strokeWidth, cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "$animatedScore",
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Medium,
                    color = PresenceColors.TextPrimary,
                    lineHeight = 52.sp
                )
                Text(
                    "PRESENCE",
                    fontSize = 9.sp,
                    letterSpacing = 3.sp,
                    color = PresenceColors.TextMuted
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    state.label,
                    fontSize = 13.sp,
                    color = ringColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        if (isLive) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // Pulsing dot
                val pulseAnim = rememberInfiniteTransition(label = "livePulse")
                val pulseAlpha by pulseAnim.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        tween(1000, easing = EaseInOut),
                        RepeatMode.Reverse
                    ),
                    label = "pulseAlpha"
                )
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(PresenceColors.AccentGreen.copy(alpha = pulseAlpha))
                )
                Text(
                    "Live — updating every second",
                    fontSize = 11.sp,
                    color = PresenceColors.AccentGreen
                )
            }
        } else {
            Text(
                "Monitoring paused",
                fontSize = 11.sp,
                color = PresenceColors.TextDim
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// NUDGE CARD
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NudgeCard(copy: String, format: String) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PresenceColors.BgNudge)
            .border(1.dp, PresenceColors.BorderNudge, RoundedCornerShape(16.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF2d1050)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (format == "HAPTIC")
                    Icons.Default.Vibration
                else Icons.Default.Notifications,
                contentDescription = null,
                tint = PresenceColors.AccentPurple,
                modifier = Modifier.size(20.dp)
            )
        }
        Column {
            Text(
                "GENTLE NUDGE",
                fontSize = 9.sp,
                letterSpacing = 1.sp,
                color = PresenceColors.AccentPurple,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                copy,
                fontSize = 13.sp,
                color = Color(0xFFd4c8f0),
                lineHeight = 20.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CONTEXT ROW (replaces 6 metric cards)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ContextRow(bleNearby: Boolean, unlockCount: Int, quickChecks: Int) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ContextCard(
            label    = "NEARBY",
            value    = if (bleNearby) "Yes" else "No",
            subLabel = if (bleNearby) "Bluetooth confirmed" else "No one detected",
            color    = if (bleNearby) PresenceColors.AccentCyan else PresenceColors.TextMuted,
            modifier = Modifier.weight(1f)
        )
        ContextCard(
            label    = "UNLOCKS",
            value    = "$unlockCount",
            subLabel = "Last 10 min",
            color    = if (unlockCount > 3) PresenceColors.AccentAmber
                       else PresenceColors.AccentGreen,
            modifier = Modifier.weight(1f)
        )
        ContextCard(
            label    = "QUICK CHECKS",
            value    = "$quickChecks",
            subLabel = "Under 20s",
            color    = if (quickChecks > 2) PresenceColors.AccentCoral
                       else PresenceColors.AccentGreen,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ContextCard(
    label: String,
    value: String,
    subLabel: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(PresenceColors.BgCard)
            .border(1.dp, PresenceColors.BorderDefault, RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Text(
            label,
            fontSize = 8.sp,
            letterSpacing = 1.sp,
            color = PresenceColors.TextDim,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = color)
        Spacer(Modifier.height(2.dp))
        Text(subLabel, fontSize = 9.sp, color = PresenceColors.TextMuted, lineHeight = 12.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LIVE SENSOR ROW
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SensorRow(voiceLevel: Float, proximityStrength: Float) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SensorCard(
            label    = "VOICE",
            value    = "${(voiceLevel).toInt()}%",
            fraction = voiceLevel / 100f,
            color    = PresenceColors.AccentGreen,
            modifier = Modifier.weight(1f)
        )
        SensorCard(
            label    = "PROXIMITY",
            value    = "${proximityStrength.toInt()}%",
            fraction = proximityStrength / 100f,
            color    = PresenceColors.AccentCyan,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SensorCard(
    label: String,
    value: String,
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val animFrac by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(800),
        label = "sensorBar"
    )
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(PresenceColors.BgCard)
            .border(1.dp, PresenceColors.BorderDefault, RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                fontSize = 8.sp,
                letterSpacing = 1.sp,
                color = PresenceColors.TextDim,
                fontWeight = FontWeight.Bold
            )
            Text(value, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = color)
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(0.05f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animFrac)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(Brush.horizontalGradient(listOf(color.copy(0.6f), color)))
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// INSIGHT CARD
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun InsightCard(text: String) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PresenceColors.BgInsight)
            .border(1.dp, PresenceColors.BorderInsight, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Text(
            "AI INSIGHT",
            fontSize = 9.sp,
            letterSpacing = 1.sp,
            color = Color(0xFF1d6a56),
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text,
            fontSize = 13.sp,
            color = Color(0xFF9de0ce),
            lineHeight = 20.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BANDIT LEARNING CARD
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BanditLearningCard(banditStats: Map<String, Any>) {
    val hapticCount = (banditStats["haptic_count"] as? Int) ?: 0
    val hapticAvg   = (banditStats["haptic_avg"] as? Float) ?: 0f
    val notifCount  = (banditStats["notif_count"] as? Int) ?: 0
    val notifAvg    = (banditStats["notif_avg"] as? Float) ?: 0f
    val preferred   = (banditStats["preferred_arm"] as? String) ?: "—"

    val hapticFrac = if (hapticAvg > 0) (hapticAvg + 1f) / 2f else 0.1f
    val notifFrac  = if (notifAvg > 0) (notifAvg + 1f) / 2f else 0.1f

    val preferredLabel = when {
        hapticCount < 5 || notifCount < 5 ->
            "Still learning your preferences..."
        hapticAvg > notifAvg ->
            "You respond ${String.format("%.1f", hapticAvg / notifAvg.coerceAtLeast(0.01f))}× better to vibrations"
        else ->
            "You respond better to notifications"
    }

    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PresenceColors.BgCard)
            .border(1.dp, PresenceColors.BorderDefault, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Text(
            "LEARNING YOUR PREFERENCES",
            fontSize = 9.sp,
            letterSpacing = 1.5.sp,
            color = PresenceColors.TextDim,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BanditArm(
                label     = "Haptic",
                value     = String.format("avg %.2f", hapticAvg),
                fraction  = hapticFrac,
                barColor  = PresenceColors.AccentPurple,
                textColor = PresenceColors.AccentGreen,
                modifier  = Modifier.weight(1f)
            )
            BanditArm(
                label     = "Notification",
                value     = String.format("avg %.2f", notifAvg),
                fraction  = notifFrac,
                barColor  = Color(0xFF2a2560),
                textColor = PresenceColors.TextMuted,
                modifier  = Modifier.weight(1f)
            )
        }

        HorizontalDivider(
            color = PresenceColors.BorderDefault,
            modifier = Modifier.padding(vertical = 10.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                preferredLabel,
                fontSize = 11.sp,
                color = PresenceColors.TextMuted,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0d2a1a))
                    .border(1.dp, Color(0xFF1a4a2e), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    preferred,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = PresenceColors.AccentGreen
                )
            }
        }
    }
}

@Composable
private fun BanditArm(
    label: String,
    value: String,
    fraction: Float,
    barColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val animFrac by animateFloatAsState(
        targetValue = fraction.coerceIn(0.05f, 1f),
        animationSpec = tween(800),
        label = "bar"
    )
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(animFrac)
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .background(barColor)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 10.sp, color = PresenceColors.TextMuted)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = textColor)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION LABEL
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 9.sp,
        letterSpacing = 1.5.sp,
        color = PresenceColors.TextDim,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .padding(bottom = 8.dp)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// SETTINGS TAB (placeholder — debug via long press in future)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsTab() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        SectionLabel("SETTINGS")
        Spacer(Modifier.height(16.dp))

        // Version info
        SettingsRow("App Version", "1.0.0")
        SettingsRow("Model", "14-feature LR + ε-Greedy Bandit")
        SettingsRow("Update Interval", "5s heartbeat, 1s ticker")

        Spacer(Modifier.height(24.dp))

        Text(
            "Debug console is available via long press on the header (coming soon).",
            fontSize = 12.sp,
            color = PresenceColors.TextMuted,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(PresenceColors.BgCard)
            .border(1.dp, PresenceColors.BorderDefault, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = PresenceColors.TextSecondary)
        Text(value, fontSize = 13.sp, color = PresenceColors.TextPrimary, fontWeight = FontWeight.Medium)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HELPERS
// ─────────────────────────────────────────────────────────────────────────────

private fun nudgeText(pPhub: Float, unlocks: Int) = when {
    unlocks >= 8  -> "You've unlocked $unlocks times recently. The conversation here is worth more."
    pPhub > 0.8f  -> "Your attention is drifting. The person with you deserves your full presence."
    else          -> "The person with you deserves your full attention."
}

private fun insightText(pPhub: Float, score: Int, unlocks: Int, micro: Int) = when {
    score >= 85 -> "Great presence — low phone engagement detected. Keep it up!"
    unlocks > 6 && micro > 3 ->
        "You've unlocked $unlocks times with $micro quick checks. Try keeping the phone face-down."
    pPhub > 0.6f -> "Elevated phone engagement detected. Try the phone-face-down technique."
    else -> "Moderate phone activity. Awareness is the first step to change."
}
