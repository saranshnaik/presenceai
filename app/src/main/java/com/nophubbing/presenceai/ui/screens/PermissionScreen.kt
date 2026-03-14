package com.nophubbing.presenceai.ui.screens

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.nophubbing.presenceai.ui.components.PermissionCard
import com.nophubbing.presenceai.ui.theme.*
import com.nophubbing.presenceai.utils.PermissionManager

@Composable
fun PermissionScreen(onStartClicked: () -> Unit) {

    val context = LocalContext.current

    var micEnabled by remember {
        mutableStateOf(PermissionManager.hasMicPermission(context))
    }

    var btEnabled by remember {
        mutableStateOf(PermissionManager.hasBluetoothPermission(context))
    }

    var notifEnabled by remember {
        mutableStateOf(
            NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micEnabled = granted
    }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val scan    = permissions[Manifest.permission.BLUETOOTH_SCAN]    ?: false
        val connect = permissions[Manifest.permission.BLUETOOTH_CONNECT] ?: false
        btEnabled = scan && connect
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgDeep, BgMid, Color(0xFF0A0820))))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            Column {
                // ── Header ──────────────────────────────────────────────────
                Text(
                    text = "Welcome to",
                    color = TextSecondary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "PresenceAI",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = (-1).sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Accent underline pill
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.horizontalGradient(listOf(PresencePurple, PresenceBlue))
                        )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "PresenceAI helps you stay present during real-world interactions " +
                            "by understanding your digital habits.",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(36.dp))

                Text(
                    text = "PERMISSIONS",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ── Permission Cards ─────────────────────────────────────────

                PermissionCard(
                    title = "Usage Activity",
                    description = "Monitor app usage and screen time patterns",
                    required = true,
                    enabled = PermissionManager.hasUsageStatsPermission(context),
                    onToggle = {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                PermissionCard(
                    title = "Notification Access",
                    description = "Detect notifications to measure notification reflex",
                    required = true,
                    enabled = notifEnabled,
                    onToggle = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        notifEnabled = NotificationManagerCompat
                            .getEnabledListenerPackages(context)
                            .contains(context.packageName)
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                PermissionCard(
                    title = "Microphone Access",
                    description = "Detect conversations to measure presence",
                    required = false,
                    enabled = micEnabled,
                    onToggle = {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                PermissionCard(
                    title = "Bluetooth Proximity",
                    description = "Identify nearby devices for social context",
                    required = false,
                    enabled = btEnabled,
                    onToggle = {
                        bluetoothPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.BLUETOOTH_SCAN,
                                Manifest.permission.BLUETOOTH_CONNECT
                            )
                        )
                    }
                )
            }

            // ── CTA Button ───────────────────────────────────────────────────

            Spacer(modifier = Modifier.height(40.dp))

            val requiredGranted = PermissionManager.hasUsageStatsPermission(context) && notifEnabled

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (requiredGranted)
                            Brush.horizontalGradient(listOf(PresencePurple, PresenceBlue))
                        else
                            Brush.horizontalGradient(listOf(Color(0xFF2A2660), Color(0xFF2A2660)))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    modifier = Modifier.fillMaxSize(),
                    onClick = onStartClicked,
                    enabled = requiredGranted,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent
                    ),
                    elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp)
                ) {
                    Text(
                        text = if (requiredGranted) "Get Started" else "Grant Required Permissions First",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (requiredGranted) Color.White else TextMuted
                    )
                }
            }
        }
    }
}
