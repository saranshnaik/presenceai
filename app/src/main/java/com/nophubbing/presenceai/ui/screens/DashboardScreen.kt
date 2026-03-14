package com.nophubbing.presenceai.ui.screens

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nophubbing.presenceai.services.MonitoringService
import com.nophubbing.presenceai.services.MonitoringState
import com.nophubbing.presenceai.ui.components.*
import com.nophubbing.presenceai.viewmodel.DashboardViewModel

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel()
) {
    val signals by viewModel.signals.collectAsState()
    val pPhub by viewModel.pPhub.collectAsState()
    val pDrift by viewModel.pDrift.collectAsState()
    val shouldNudge by viewModel.shouldNudge.collectAsState()
    val accuracy by viewModel.accuracy.collectAsState()

    val context = LocalContext.current
    // Observe the global service state reactively
    val isRunning by MonitoringState.isRunning.collectAsState()

    val unlocks = signals?.unlocks ?: 0
    val totalSessions = signals?.totalSessions ?: 0
    val microSessions = signals?.microSessions ?: 0
    val notifReflex = signals?.notificationReflex ?: 0
    val driftScore = signals?.pDrift ?: 0f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF0F0C29), Color(0xFF302B63), Color(0xFF24243E))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Presence AI", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text("Observing gracefully", color = Color.White.copy(alpha = 0.5f), fontSize = 16.sp)
                }

                Button(
                    onClick = {
                        val intent = Intent(context, MonitoringService::class.java)
                        if (!isRunning) {
                            context.startForegroundService(intent)
                        } else {
                            context.stopService(intent)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) Color(0xFF00C6FF) else Color(0xFFFF007A)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (isRunning) "On" else "Off", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                PresenceCircle(pPhub = pPhub)
            }

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedVisibility(
                visible = shouldNudge,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFF007A).copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⚠️ Elevated Phubbing Detected. Stay present!", color = Color(0xFFFF7EB3), fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Accuracy & Drift Status
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Text("MODEL ACCURACY", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${(accuracy * 100).toInt()}%", 
                            color = Color(0xFF00C6FF), 
                            fontSize = 20.sp, 
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Text("DRIFT SCORE", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = String.format("%.2f", driftScore), 
                            color = Color(0xFFFF007A), 
                            fontSize = 20.sp, 
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Metrics Grid
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricCard(
                    title = "SESSIONS", 
                    value = totalSessions.toString(), 
                    subtitle = "App switches", 
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(16.dp))
                MetricCard(
                    title = "UNLOCKS", 
                    value = unlocks.toString(), 
                    subtitle = "Device wakeups", 
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                MetricCard(
                    title = "MICRO SESH", 
                    value = microSessions.toString(), 
                    subtitle = "Under 30s", 
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(16.dp))
                MetricCard(
                    title = "NOTIF REFLX", 
                    value = if (notifReflex >= 1) "YES" else "NO", 
                    subtitle = "Response event", 
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            InsightCard(
                insight = if (pPhub > 0.5) "Your device engagement is rising. Consider keeping the phone face-down."
                else "Great job staying present. Low phone engagement detected."
            )
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
