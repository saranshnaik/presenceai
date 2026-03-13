package com.nophubbing.presenceai

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.nophubbing.presenceai.services.UsageStatsCollector
import com.nophubbing.presenceai.utils.PermissionManager
import com.nophubbing.presenceai.services.UsageEventsCollector
import com.nophubbing.presenceai.analytics.FeatureExtractor
import com.nophubbing.presenceai.services.MonitoringService

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var usageButton: Button
    private lateinit var micButton: Button
    private lateinit var bluetoothButton: Button
    private lateinit var usageEventsCollector: UsageEventsCollector
    private lateinit var featureExtractor: FeatureExtractor

    private lateinit var usageCollector: UsageStatsCollector

    private val micPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private val bluetoothPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        val serviceIntent = Intent(this, MonitoringService::class.java)
        startForegroundService(serviceIntent)
        super.onCreate(savedInstanceState)

        usageCollector = UsageStatsCollector(this)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50,200,50,50)

        statusText = TextView(this)
        statusText.textSize = 20f

        usageButton = Button(this)
        micButton = Button(this)
        bluetoothButton = Button(this)

        usageButton.text = "Grant Usage Access"
        micButton.text = "Enable Microphone (Optional)"
        bluetoothButton.text = "Enable Bluetooth (Optional)"

        layout.addView(statusText)
        layout.addView(usageButton)
        layout.addView(micButton)
        layout.addView(bluetoothButton)

        setContentView(layout)

        usageButton.setOnClickListener {

            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            startActivity(intent)

        }

        micButton.setOnClickListener {

            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)

        }

        bluetoothButton.setOnClickListener {

            bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)

        }

        updateUI()
        usageEventsCollector = UsageEventsCollector(this)
        featureExtractor = FeatureExtractor(this)

    }

    override fun onResume() {
        super.onResume()

        updateUI()

        if (PermissionManager.hasUsageStatsPermission(this)) {

            usageCollector.printUsageStats()

            usageEventsCollector.printRecentForegroundEvents()

            featureExtractor.extractFeatures()
        }
    }

    private fun updateUI() {

        val usage = PermissionManager.hasUsageStatsPermission(this)
        val mic = PermissionManager.hasMicPermission(this)
        val bt = PermissionManager.hasBluetoothPermission(this)

        statusText.text =
            "Permissions Status\n\n" +
                    "Usage Access: $usage\n" +
                    "Microphone: $mic\n" +
                    "Bluetooth: $bt"
    }
}