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
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import com.nophubbing.presenceai.services.MonitoringService
import com.nophubbing.presenceai.ui.screens.DashboardScreen
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
                    .background(Color(0xFF080616))
            ) {
                DashboardScreen()
            }
        }

        // Auto-start monitoring service
        startForegroundService(Intent(this, MonitoringService::class.java))
    }

    override fun onResume() {
        super.onResume()
        // Optionally verify permissions here
    }
}
