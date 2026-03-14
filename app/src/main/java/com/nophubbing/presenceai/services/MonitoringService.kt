package com.nophubbing.presenceai.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.util.Log
import androidx.core.app.NotificationCompat
import com.nophubbing.presenceai.analytics.FeatureExtractor
import com.nophubbing.presenceai.analytics.SignalAggregator
import com.nophubbing.presenceai.analytics.SignalRepository
import com.nophubbing.presenceai.ml.PipelineConfig
import com.nophubbing.presenceai.storage.CSVLogger
import kotlinx.coroutines.*

object MonitoringState {
    val isRunning = kotlinx.coroutines.flow.MutableStateFlow(false)
}

/**
 * MonitoringService — ForegroundService, START_STICKY.
 *
 * Each heartbeat cycle (30s default from PipelineConfig):
 *  1. VoiceMonitor.detectVoice()      → featureExtractor.currentVadEnergy
 *  2. ProximityMonitor.detectProximity() → featureExtractor.currentBleSocial
 *  3. FeatureExtractor.extractFeatures()  — UsageStats + VAD/BLE values from above
 *  4. SignalAggregator.generateSignals()  → BehaviorSignals
 *  5. SignalRepository.update()           → ViewModel via StateFlow
 *  6. CSVLogger.logSignals()              → on-device CSV for historical training
 *
 * VAD/BLE both run with try-catch. Failure → 0.0 (safe default for 14-feature model).
 * OEM note: ColorOS/Xiaomi kill services. Keep screen on for demo. START_STICKY restarts.
 */
class MonitoringService : Service() {

    private val config = PipelineConfig()
    private val scope  = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var featureExtractor: FeatureExtractor
    private lateinit var signalAggregator: SignalAggregator
    private lateinit var voiceMonitor:     VoiceMonitor
    private lateinit var proximityMonitor: ProximityMonitor
    private lateinit var csvLogger:        CSVLogger

    companion object {
        private const val CHANNEL_ID      = "presence_monitoring"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        featureExtractor = FeatureExtractor(this)
        signalAggregator = SignalAggregator(this)
        voiceMonitor     = VoiceMonitor(this)
        proximityMonitor = ProximityMonitor(this)
        csvLogger        = CSVLogger(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        MonitoringState.isRunning.value = true
        Log.d("PresenceAI", "MonitoringService Created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startHeartbeat()
        return START_STICKY
    }

    private fun startHeartbeat() {
        scope.launch {
            while (isActive) {
                try { collectAndSaveSignals() }
                catch (e: Exception) { Log.e("PresenceAI", "Heartbeat error: ${e.message}") }
                delay(config.heartbeat_interval_ms)
            }
        }
    }

    private fun collectAndSaveSignals() {
        Log.d("PresenceAI", "Collecting live signals for ML pipeline...")

        // VAD — detectVoice() now returns a float confidence score.
        // Negative values mean permission denied / hardware error → treat as 0 (no voice).
        featureExtractor.currentVadEnergy = try {
            val v = voiceMonitor.detectVoice()
            if (v < 0f) 0f else v          // v is already 0.0–1.0
        } catch (e: Exception) { 0f }

        // BLE proximity — detectProximity() returns a float confidence score.
        // 0.9 = connected paired device, 0.5 = anonymous BLE nearby, 0 = none, <0 = error.
        featureExtractor.currentBleSocial = try {
            val p = proximityMonitor.detectProximity()
            if (p < 0f) 0f else p          // p is already 0.0–1.0
        } catch (e: Exception) { 0f }

        val features = featureExtractor.extractFeatures(windowMinutes = 10)
        val signals  = signalAggregator.generateSignals(features)

        SignalRepository.update(signals)
        csvLogger.logSignals(signals)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        MonitoringState.isRunning.value = false
        Log.d("PresenceAI", "MonitoringService Destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "Presence AI Monitoring", NotificationManager.IMPORTANCE_LOW
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
