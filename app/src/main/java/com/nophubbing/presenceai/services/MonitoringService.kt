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
import com.nophubbing.presenceai.analytics.FeatureExtractor
import com.nophubbing.presenceai.analytics.SignalAggregator
import com.nophubbing.presenceai.analytics.SignalRepository
import com.nophubbing.presenceai.storage.CSVLogger
import com.nophubbing.presenceai.utils.PermissionManager

class MonitoringService : Service() {

//    private val handler = Handler(Looper.getMainLooper())
    private val voiceMonitor = VoiceMonitor()
    private val proximityMonitor by lazy { ProximityMonitor(this) }

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

        val features = featureExtractor.extractFeatures(windowMinutes = 1)

        val unlocks = UnlockCounter.unlockCount
        val notifications = NotificationCounter.notificationCount

        val micAllowed = PermissionManager.hasMicPermission(this)
        val bluetoothAllowed = PermissionManager.hasBluetoothPermission(this)
        val voiceDetected =
            if (micAllowed) voiceMonitor.detectVoice()
            else -1
        val proximityDetected =
            if (bluetoothAllowed) proximityMonitor.detectProximity()
            else -1

        val signals = signalAggregator.generateSignals(
            unlocks = unlocks,
            microSessions = features.microSessions,
            notificationReflex = features.notificationReflex,
            behaviorDrift = 0f,
            voiceDetected = voiceDetected,
            proximityDetected = proximityDetected,
            micAllowed = micAllowed,
            bluetoothAllowed = bluetoothAllowed
        )
        SignalRepository.update(signals)

        csvLogger.logSignals(signals)

        UnlockCounter.unlockCount = 0
        NotificationCounter.notificationCount = 0
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