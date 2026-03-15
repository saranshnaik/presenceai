package com.nophubbing.presenceai

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.nophubbing.presenceai.services.MonitoringService
import com.nophubbing.presenceai.ui.screens.DashboardScreen
import com.nophubbing.presenceai.ui.theme.PresenceColors
import com.nophubbing.presenceai.utils.PermissionManager

class MainActivity : ComponentActivity() {

    private val usagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* re-check after returning */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge UI
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT

        setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PresenceColors.BgDeep)
            ) {
                DashboardScreen()
            }
        }

        requestAllPermissions()
        
        // Auto-start monitoring service
        startForegroundService(Intent(this, MonitoringService::class.java))
    }

    private fun requestAllPermissions() {
        val permissions = mutableListOf<String>()
        
        permissions.add(android.Manifest.permission.RECORD_AUDIO)
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            permissions.add(android.Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(android.Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        val toRequest = permissions.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(this, it) != 
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (toRequest.isNotEmpty()) {
            requestPermissions(toRequest.toTypedArray(), 101)
        }
    }

    override fun onResume() {
        super.onResume()
        // Optionally verify permissions here
    }
}
