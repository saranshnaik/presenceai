package com.nophubbing.presenceai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.nophubbing.presenceai.services.MonitoringService
import com.nophubbing.presenceai.ui.screens.DashboardScreen
import com.nophubbing.presenceai.utils.PermissionManager

class MainActivity : ComponentActivity() {

    // ── Permission launchers ──────────────────────────────────────────────────

    private val usagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* re-check on resume */ }

    /**
     * Multi-permission launcher for RECORD_AUDIO + Bluetooth permissions.
     * Called once on first launch; subsequent launches skip if already granted.
     */
    private val runtimePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val micGranted = grants[Manifest.permission.RECORD_AUDIO] == true
        val btScanGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            grants[Manifest.permission.BLUETOOTH_SCAN] == true else true
        val btConnectGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            grants[Manifest.permission.BLUETOOTH_CONNECT] == true else true
        val locationGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true

        android.util.Log.d("PresenceAI", "Permissions granted: " +
                "mic=$micGranted btScan=$btScanGranted btConnect=$btConnectGranted loc=$locationGranted")
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val context = this

        // Edge-to-edge UI
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT

        setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF080616))
            ) {
                DashboardScreen()
            }
        }

        // Request runtime permissions needed by VAD + Bluetooth proximity
        requestMissingPermissions()

        // Auto-start monitoring service
        startForegroundService(Intent(this, MonitoringService::class.java))
    }

    override fun onResume() {
        super.onResume()
        // If user just came back from Usage Stats settings screen, no action needed —
        // MonitoringService reads UsageStats on its own heartbeat.
    }

    // ── Permission helpers ────────────────────────────────────────────────────

    private fun requestMissingPermissions() {
        val needed = mutableListOf<String>()

        // Microphone — required for VAD
        if (!has(Manifest.permission.RECORD_AUDIO)) {
            needed += Manifest.permission.RECORD_AUDIO
        }

        // Bluetooth — API-level conditional
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ needs explicit BLUETOOTH_SCAN + BLUETOOTH_CONNECT
            if (!has(Manifest.permission.BLUETOOTH_SCAN))    needed += Manifest.permission.BLUETOOTH_SCAN
            if (!has(Manifest.permission.BLUETOOTH_CONNECT)) needed += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            // Android < 12: Bluetooth discovery requires location permission
            if (!has(Manifest.permission.ACCESS_FINE_LOCATION)) {
                needed += Manifest.permission.ACCESS_FINE_LOCATION
            }
        }

        if (needed.isNotEmpty()) {
            runtimePermissionLauncher.launch(needed.toTypedArray())
        }

        // Usage stats is a special permission — direct user to settings if missing
        if (!PermissionManager.hasUsageStatsPermission(this)) {
            PermissionManager.openUsageStatsSettings(this)
        }
    }

    private fun has(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
