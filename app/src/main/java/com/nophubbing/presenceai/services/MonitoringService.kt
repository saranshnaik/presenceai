package com.nophubbing.presenceai.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.nophubbing.presenceai.analytics.FeatureExtractor
import com.nophubbing.presenceai.analytics.SignalAggregator
import com.nophubbing.presenceai.analytics.SignalRepository
import com.nophubbing.presenceai.ml.PipelineConfig
import com.nophubbing.presenceai.storage.CSVLogger
import com.nophubbing.presenceai.utils.PermissionManager
import kotlinx.coroutines.*

class MonitoringService : Service() {

    // private val config = PipelineConfig() // Removed: PipelineConfig is now an object
    private val scope  = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var featureExtractor : FeatureExtractor
    private lateinit var signalAggregator : SignalAggregator
    private lateinit var voiceMonitor     : VoiceMonitor
    private lateinit var proximityMonitor : ProximityMonitor
    private lateinit var csvLogger        : CSVLogger

    companion object {
        private const val TAG             = "PresenceAI"
        private const val CHANNEL_ID      = "presence_monitoring"
        private const val NOTIFICATION_ID = 1001
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        featureExtractor = FeatureExtractor(this)
        signalAggregator = SignalAggregator(this)
        voiceMonitor     = VoiceMonitor(this)
        proximityMonitor = ProximityMonitor(this)
        csvLogger        = CSVLogger(this)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        MonitoringState.setRunning(true)   // StateFlow → DashboardScreen recomposes
        Log.d(TAG, "MonitoringService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startHeartbeat()
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        MonitoringState.setRunning(false)  // StateFlow → DashboardScreen recomposes
        Log.d(TAG, "MonitoringService destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Heartbeat ─────────────────────────────────────────────────────────────

    private fun startHeartbeat() {
        scope.launch {
            while (isActive) {
                try { collectAndSaveSignals() }
                catch (e: Exception) { Log.e(TAG, "Heartbeat error: ${e.message}") }
                delay(3_000L) // Capture every 3 seconds as requested
            }
        }
    }

    private fun collectAndSaveSignals() {
        Log.d(TAG, "Collecting signals...")

        val micAllowed = PermissionManager.hasMicPermission(this)
        val btAllowed  = PermissionManager.hasBluetoothPermission(this)

        // VAD — 0f if no permission or failure
        val vadEnergy = try {
            if (micAllowed) voiceMonitor.detectVoice() else 0f
        } catch (e: Exception) { 0f }

        // BLE — -100f if no permission or failure
        val bleRssi = try {
            if (btAllowed) proximityMonitor.detectProximity() else -100f
        } catch (e: Exception) { -100f }

        // Step 1: set social context on extractor, then extract usage-stats features
        featureExtractor.currentVadEnergy = vadEnergy
        featureExtractor.currentBleSocial = bleRssi
        val features = featureExtractor.extractFeatures(windowMinutes = 10)

        // Step 2: aggregate into BehaviorSignals
        val signals = signalAggregator.generateSignals(features)

        // Step 3: publish to repository → ViewModel → UI
        SignalRepository.update(signals)

        // Step 4: persist to CSV for offline training
        csvLogger.logSignals(signals)

        // Step 5: reset per-tick counters AFTER logging
        UnlockCounter.unlockCount             = 0
        NotificationCounter.notificationCount = 0

        Log.d(TAG, "Signals saved — unlocks=${signals.unlocks} vad=$vadEnergy ble=$bleRssi")
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Presence AI Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Background attention signal monitoring" }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Presence AI")
            .setContentText("Observing gracefully…")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
}