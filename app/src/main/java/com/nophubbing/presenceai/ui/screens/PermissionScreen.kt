package com.nophubbing.presenceai.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nophubbing.presenceai.ui.components.PermissionCard
import com.nophubbing.presenceai.utils.PermissionManager

@Composable
fun PermissionScreen(onStartClicked: () -> Unit) {

    val context = LocalContext.current

    var micEnabled by remember { mutableStateOf(PermissionManager.hasMicPermission(context)) }
    var btEnabled by remember { mutableStateOf(PermissionManager.hasBluetoothPermission(context)) }

    val micPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            micEnabled = granted
        }

    val bluetoothPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            btEnabled = permissions.values.all { it }
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {

        Column {

            Text(
                text = "Welcome to PresenceAI",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "PresenceAI helps you stay present during real-world interactions by understanding your digital habits."
            )

            Spacer(modifier = Modifier.height(32.dp))

            PermissionCard(
                title = "Usage Activity",
                description = "Monitor app usage and screen time patterns",
                required = true,
                enabled = PermissionManager.hasUsageStatsPermission(context),
                onToggle = {

                    val intent =
                        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

                    context.startActivity(intent)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            PermissionCard(
                title = "Microphone Access",
                description = "Detect conversations to measure presence",
                required = false,
                enabled = micEnabled,
                onToggle = {
                    micPermissionLauncher.launch(
                        Manifest.permission.RECORD_AUDIO
                    )
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            PermissionCard(
                title = "Bluetooth Proximity",
                description = "Identify nearby devices for social context",
                required = false,
                enabled = btEnabled,
                onToggle = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        bluetoothPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.BLUETOOTH_SCAN,
                                Manifest.permission.BLUETOOTH_CONNECT
                            )
                        )
                    } else {
                        bluetoothPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION
                            )
                        )
                    }
                }
            )
        }

        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            onClick = onStartClicked
        ) {
            Text("Get Started")
        }
    }
}