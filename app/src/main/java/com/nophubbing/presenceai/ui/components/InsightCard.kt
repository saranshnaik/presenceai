package com.nophubbing.presenceai.ui.components

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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.nophubbing.presenceai.rl.NudgeFormat
import com.nophubbing.presenceai.ui.theme.*

// ── NudgeAlertV2 — Claude-powered nudge banner with "Why?" ─────────────────
@Composable
fun NudgeAlertV2(
    visible: Boolean,
    nudgeFormat: NudgeFormat?,
    nudgeCopy: String,
    nudgeExplanation: String?,
    isLoadingExplanation: Boolean,
    onWhyClicked: () -> Unit,
    onDismissExplanation: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)) + expandVertically(expandFrom = Alignment.Top),
        exit = fadeOut(tween(200)) + shrinkVertically(shrinkTowards = Alignment.Top),
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF3A0025), Color(0xFF180038))))
                    .border(1.dp, PresencePink.copy(0.5f), RoundedCornerShape(20.dp)).padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PulsingFormatIcon(nudgeFormat)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (nudgeFormat == NudgeFormat.HAPTIC) "Haptic Nudge" else "Presence Reminder",
                            color = PresencePink, fontSize = 10.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(nudgeCopy, color = Color.White.copy(0.92f), fontSize = 14.sp, lineHeight = 21.sp)
                        Spacer(Modifier.height(10.dp))
                        Box(
                            Modifier.clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(0.06f))
                                .clickable(onClick = onWhyClicked)
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isLoadingExplanation) {
                                    CircularProgressIndicator(color = PresencePink,
                                        modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                                } else {
                                    Icon(Icons.Default.HelpOutline, null,
                                        tint = PresencePink.copy(0.8f), modifier = Modifier.size(12.dp))
                                }
                                Spacer(Modifier.width(5.dp))
                                Text("Why did I get this?", color = PresencePink.copy(0.8f),
                                    fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            // Claude explanation card
            AnimatedVisibility(visible = nudgeExplanation != null,
                enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                nudgeExplanation?.let { explanation ->
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF0D0A20))
                            .border(1.dp, PresencePurple.copy(0.3f), RoundedCornerShape(16.dp)).padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                Modifier.size(28.dp).clip(CircleShape).background(PresencePurple.copy(0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.AutoAwesome, null, tint = PresencePurple, modifier = Modifier.size(14.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("CLAUDE EXPLAINS", color = PresencePurple, fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                                Spacer(Modifier.height(5.dp))
                                Text(explanation, color = TextSecondary, fontSize = 13.sp, lineHeight = 20.sp)
                            }
                            IconButton(onClick = onDismissExplanation, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, null, tint = TextMuted, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PulsingFormatIcon(format: NudgeFormat?) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(1f, 1.15f,
        infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "s")
    Box(
        Modifier.size(48.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(14.dp)).background(PresencePink.copy(0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (format == NudgeFormat.HAPTIC) Icons.Default.Vibration else Icons.Default.Notifications,
            null, tint = PresencePink, modifier = Modifier.size(24.dp)
        )
    }
}

// ── InsightCard ───────────────────────────────────────────────────────────────
@Composable
fun InsightCard(insight: String, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1A1050), Color(0xFF0D0A2A))))
            .border(1.dp, PresencePurple.copy(0.3f), RoundedCornerShape(20.dp)).padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(PresencePurple.copy(0.2f)),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Info, null, tint = PresencePurple, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("AI INSIGHT", color = PresencePurple, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(6.dp))
                Text(insight, color = Color.White.copy(0.88f), fontSize = 14.sp, lineHeight = 21.sp)
            }
        }
    }
}

// ── BanditStatsCard ───────────────────────────────────────────────────────────
@Composable
fun BanditStatsCard(stats: Map<String, Any>, modifier: Modifier = Modifier) {
    val hapticCount = (stats["haptic_count"] as? Int) ?: 0
    val hapticAvg   = (stats["haptic_avg"]   as? Float) ?: 0f
    val notifCount  = (stats["notif_count"]  as? Int) ?: 0
    val notifAvg    = (stats["notif_avg"]    as? Float) ?: 0f
    val preferred   = (stats["preferred_arm"] as? String) ?: "—"

    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF0A1A30), Color(0xFF070E1A))))
            .border(1.dp, PresenceBlue.copy(0.2f), RoundedCornerShape(20.dp)).padding(20.dp)
    ) {
        Column {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("RL NUDGE OPTIMIZER", color = TextMuted, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                val prefColor = if (preferred == "HAPTIC") PresenceGreen else PresenceBlue
                Box(Modifier.clip(RoundedCornerShape(6.dp)).background(prefColor.copy(0.15f))
                    .border(1.dp, prefColor.copy(0.4f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text(preferred, color = prefColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ArmStat("HAPTIC", hapticCount, hapticAvg, PresenceGreen, Modifier.weight(1f))
                ArmStat("NOTIF", notifCount, notifAvg, PresenceBlue, Modifier.weight(1f))
            }
            if (hapticCount + notifCount > 0) {
                Spacer(Modifier.height(14.dp))
                val hFrac = hapticCount.toFloat() / (hapticCount + notifCount)
                val animFrac by animateFloatAsState(hFrac, tween(800), label = "bFrac")
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("Trial split", color = TextMuted, fontSize = 10.sp)
                    Text("${(hFrac*100).toInt()}% haptic · ${(100-hFrac*100).toInt()}% notif",
                        color = TextSecondary, fontSize = 10.sp)
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(0.06f))) {
                    Box(Modifier.fillMaxWidth(animFrac).fillMaxHeight().clip(RoundedCornerShape(3.dp))
                        .background(Brush.horizontalGradient(listOf(PresenceGreen.copy(0.8f), PresenceGreen))))
                    Box(Modifier.fillMaxWidth(1f - animFrac).fillMaxHeight().align(Alignment.CenterEnd)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Brush.horizontalGradient(listOf(PresenceBlue, PresenceBlue.copy(0.8f)))))
                }
            }
        }
    }
}

@Composable
private fun ArmStat(label: String, count: Int, avg: Float, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(color.copy(0.06f))
        .border(1.dp, color.copy(if (avg > 0) 0.3f else 0.1f), RoundedCornerShape(12.dp)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (label == "HAPTIC") Icons.Default.Vibration else Icons.Default.Notifications,
                null, tint = color, modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text("$count", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp)
        Text("trials", color = TextMuted, fontSize = 10.sp)
        Spacer(Modifier.height(4.dp))
        val rewardColor = when { avg > 0.3f -> PresenceGreen; avg < -0.1f -> PresencePink; else -> PresenceOrange }
        Text(String.format("%+.2f", avg), color = rewardColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text("avg reward", color = TextMuted, fontSize = 10.sp)
    }
}
