package com.nophubbing.presenceai.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import android.util.Log
import com.nophubbing.presenceai.analytics.FeatureExtractor
import com.nophubbing.presenceai.analytics.SignalAggregator
import com.nophubbing.presenceai.analytics.SignalRepository
import com.nophubbing.presenceai.storage.CSVLogger
import com.nophubbing.presenceai.utils.PermissionManager
import com.nophubbing.presenceai.signals.UnlockTracker
import com.nophubbing.presenceai.signals.SessionTracker
import com.nophubbing.presenceai.signals.NotificationMonitor
import com.nophubbing.presenceai.signals.VadScanner
import com.nophubbing.presenceai.signals.BleScanner
import java.util.Calendar

class MonitoringService : Service() {

    private val vadScanner by lazy { VadScanner(this) }
    private val bleScanner by lazy { BleScanner(this) }

    private lateinit var featureExtractor: FeatureExtractor
    private lateinit var signalAggregator: SignalAggregator
    private lateinit var csvLogger: CSVLogger

    private val interval: Long = 60 * 1000   // 1 minute

    private var running = true


    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()
        MonitoringState.isRunning = true

        featureExtractor = FeatureExtractor(this)
        signalAggregator = SignalAggregator(this)
        csvLogger = CSVLogger(this)


//        handler.post(monitorTask)
    }

    private fun collectAndSaveSignals() {
        val now = System.currentTimeMillis()

        // x1: unlock_freq
        val rawUnlocks = UnlockTracker.getUnlockCount10Min()
        val x1 = (rawUnlocks.toFloat() / 3.0f).coerceIn(0.0f, 4.0f)

        // x2: micro_session_ratio
        val x2 = SessionTracker.getMicroSessionRatio(this).coerceIn(0.0f, 1.0f)

        // x3: notification_reflex (1.0 if notif in last 5s)
        val lastNotif = NotificationMonitor.lastNotificationTimeMs
        val notifDelta = now - lastNotif
        val x3 = if (lastNotif > 0L && notifDelta <= 5000L) 1.0f else 0.0f

        // x4: behavior_drift_z
        val x4 = 0.0f // Defaults to 0 since no baseline

        // x5: time_phase (hour lookup)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val x5 = when(hour) { in 5..11 -> 0.2f; in 12..16 -> 0.5f; in 17..21 -> 0.8f; else -> 0.1f }

        // x6: vad_energy (Voice)
        val x6 = vadScanner.getVadEnergy()

        // x7: ble_social (Bluetooth)
        val x7 = bleScanner.getBleSocial()

        Log.d("PresenceAI_Features", "--- Feature Extraction Log ---")
        Log.d("PresenceAI_Features", "x1_unlock_freq         : $x1")
        Log.d("PresenceAI_Features", "x2_micro_session_ratio : $x2")
        Log.d("PresenceAI_Features", "x3_notification_reflex : $x3")
        Log.d("PresenceAI_Features", "x4_behavior_drift_z    : $x4")
        Log.d("PresenceAI_Features", "x5_time_phase          : $x5")
        Log.d("PresenceAI_Features", "x6_vad_energy          : $x6")
        Log.d("PresenceAI_Features", "x7_ble_social          : $x7")
        Log.d("PresenceAI_Features", "------------------------------")

        val signals = signalAggregator.generateSignals(x1, x2, x3, x4, x5, x6, x7)
        SignalRepository.update(signals)
        csvLogger.logSignals(signals)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        Thread {

            while (running) {

                try {
                    collectAndSaveSignals()
                    Thread.sleep(interval)

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

        }.start()

        return START_STICKY
    }

    private fun startForegroundServiceNotification() {

        val channelId = "presenceai_monitor"

        val channel = NotificationChannel(
            channelId,
            "PresenceAI Monitoring",
            NotificationManager.IMPORTANCE_LOW
        )

        val manager =
            getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(channel)

        val notification: Notification =
            NotificationCompat.Builder(this, channelId)
                .setContentTitle("PresenceAI")
                .setContentText("Monitoring phone usage patterns")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .build()

        startForeground(1, notification)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    override fun onDestroy() {
        MonitoringState.isRunning = false
        running = false
        super.onDestroy()
    }
}