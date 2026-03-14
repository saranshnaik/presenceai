package com.nophubbing.presenceai.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nophubbing.presenceai.rl.NudgeFormat
import com.nophubbing.presenceai.ui.theme.*

@Composable
fun NudgeAlert(
    visible: Boolean,
    nudgeFormat: NudgeFormat?,
    nudgeText: String = "The person with you deserves your full attention.",
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
        exit  = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF3D0028), Color(0xFF1A0040))
                    )
                )
                .border(1.dp, PresencePink.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Format icon
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PresencePink.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (nudgeFormat == NudgeFormat.HAPTIC)
                            Icons.Default.Vibration else Icons.Default.Notifications,
                        contentDescription = "Nudge format",
                        tint = PresencePink,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = if (nudgeFormat == NudgeFormat.HAPTIC) "Haptic Nudge" else "Notification Nudge",
                        color = PresencePink, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = nudgeText,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp, lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

@Composable
fun InsightCard(
    insight: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(listOf(Color(0xFF1A1050), Color(0xFF0D0A2A)))
            )
            .border(1.dp, PresencePurple.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(PresencePurple.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Info, null, tint = PresencePurple, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("AI INSIGHT", color = PresencePurple, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(6.dp))
                Text(insight, color = Color.White.copy(alpha = 0.88f),
                    fontSize = 14.sp, lineHeight = 21.sp)
            }
        }
    }
}

@Composable
fun BanditStatsCard(stats: Map<String, Any>, modifier: Modifier = Modifier) {
    val hapticCount = (stats["haptic_count"] as? Int) ?: 0
    val hapticAvg   = (stats["haptic_avg"] as? Float) ?: 0f
    val notifCount  = (stats["notif_count"] as? Int) ?: 0
    val notifAvg    = (stats["notif_avg"] as? Float) ?: 0f
    val preferred   = (stats["preferred_arm"] as? String) ?: "—"

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF0A1A30), Color(0xFF070E1A))))
            .border(1.dp, PresenceBlue.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Column {
            Text("NUDGE LEARNING", color = TextMuted, fontSize = 10.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                ArmStat("HAPTIC", hapticCount, hapticAvg, PresenceGreen, Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                ArmStat("NOTIF", notifCount, notifAvg, PresenceBlue, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Preferred: ", color = TextMuted, fontSize = 12.sp)
                StatusPill(preferred, if (preferred == "HAPTIC") PresenceGreen else PresenceBlue)
            }
        }
    }
}

@Composable
private fun ArmStat(label: String, count: Int, avg: Float, color: Color, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(label, color = color, fontSize = 10.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(4.dp))
        Text("$count trials", color = TextSecondary, fontSize = 12.sp)
        Text(
            text = "avg ${String.format("%.2f", avg)}",
            color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold
        )
    }
}
