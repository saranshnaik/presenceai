package com.nophubbing.presenceai.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nophubbing.presenceai.services.MonitoringService
import com.nophubbing.presenceai.ui.components.*
import com.nophubbing.presenceai.viewmodel.DashboardViewModel
import com.nophubbing.presenceai.services.*

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel()
) {

    val signals by viewModel.signals.collectAsState()

    val context = LocalContext.current

    var monitoring by remember { mutableStateOf(MonitoringState.isRunning) }

    val unlocks = signals?.unlocks ?: 0
    val microSessions = signals?.microSessions ?: 0
    val notifReflex = signals?.notificationReflex ?: 0

    val presenceScore =
        (100 - (unlocks * 2 + microSessions * 3))
            .coerceIn(0, 100)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {

        Text(
            "Good morning",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            "Your Presence",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            if (monitoring) "Monitoring Active" else "Monitoring Paused",
            color =
                if (monitoring)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error
        )
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            onClick = {

                val intent = Intent(context, MonitoringService::class.java)

                if (!MonitoringState.isRunning) {

                    context.startForegroundService(intent)
                    MonitoringState.isRunning = true

                } else {

                    context.stopService(intent)
                    MonitoringState.isRunning = false
                }

                monitoring = MonitoringState.isRunning
            }
        )
        {
            Text(if (monitoring) "Stop Monitoring" else "Start Monitoring")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {

            PresenceCircle(score = presenceScore)

        }

        Spacer(modifier = Modifier.height(24.dp))

        Row {

            MetricCard(
                title = "DRIFT RISK",
                value = if (presenceScore > 70) "LOW" else "HIGH",
                subtitle = "Stay mindful",
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(16.dp))

            MetricCard(
                title = "UNLOCKS",
                value = unlocks.toString(),
                subtitle = "Today",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row {

            MetricCard(
                title = "QUICK CHECKS",
                value = microSessions.toString(),
                subtitle = "Under 30s",
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(16.dp))

            MetricCard(
                title = "NOTIF REFLEX",
                value = notifReflex.toString(),
                subtitle = "Response events",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        InsightCard()
    }
}