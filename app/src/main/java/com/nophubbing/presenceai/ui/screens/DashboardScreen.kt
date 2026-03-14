package com.nophubbing.presenceai.ui.screens

import android.content.Intent
import java.util.Calendar
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nophubbing.presenceai.services.MonitoringService
import com.nophubbing.presenceai.services.MonitoringState
import com.nophubbing.presenceai.ui.components.*
import com.nophubbing.presenceai.viewmodel.DashboardViewModel

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel()
) {

    val signals by viewModel.signals.collectAsState()
    val context = LocalContext.current

    val monitoring = MonitoringState.isRunning

    val unlocks = signals?.unlocks ?: 0
    val microSessions = signals?.microSessions ?: 0
    val notifReflex = signals?.notificationReflex ?: 0
    val behaviorDrift = signals?.behaviorDrift ?: 0f

    val presenceScore =
        (100 -
                (unlocks * 2 +
                        microSessions * 3 +
                        notifReflex * 4 +
                        (behaviorDrift * 20).toInt())
                ).coerceIn(0, 100)

    val greeting = remember { getGreeting() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {

        Text(
            greeting,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            "Your Presence",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            if (monitoring) "Monitoring Active" else "Monitoring Paused",
            color =
                if (monitoring)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(12.dp))

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
            }
        ) {
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
                value = if (behaviorDrift > 0.4) "HIGH" else "LOW",
                subtitle = "Behavior deviation",
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(16.dp))

            MetricCard(
                title = "UNLOCKS",
                value = unlocks.toString(),
                subtitle = "Last interval",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row {

            MetricCard(
                title = "QUICK CHECKS",
                value = microSessions.toString(),
                subtitle = "Micro sessions",
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(16.dp))

            MetricCard(
                title = "NOTIF REFLEX",
                value = notifReflex.toString(),
                subtitle = "Fast responses",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        InsightCard()
    }
}

private fun getGreeting(): String {

    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Good night"
    }
}