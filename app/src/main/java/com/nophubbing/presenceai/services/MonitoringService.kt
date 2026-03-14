package com.nophubbing.presenceai.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.nophubbing.presenceai.analytics.FeatureExtractor
import com.nophubbing.presenceai.analytics.SignalAggregator
import com.nophubbing.presenceai.analytics.SignalRepository
import com.nophubbing.presenceai.storage.CSVLogger
import com.nophubbing.presenceai.utils.PermissionManager

class MonitoringService : Service() {

    private val voiceMonitor by lazy { VoiceMonitor(this) }
    private val proximityMonitor by lazy { ProximityMonitor(this) }

    private lateinit var featureExtractor: FeatureExtractor
    private lateinit var signalAggregator: SignalAggregator
    private lateinit var csvLogger: CSVLogger
    private lateinit var unlockDetector: UnlockDetector

    private val interval: Long = 60 * 1000

    private var running = false
    private var monitoringThread: Thread? = null

    override fun onCreate() {
        super.onCreate()

        startForegroundServiceNotification()

        MonitoringState.isRunning = true

        featureExtractor = FeatureExtractor(this)
        signalAggregator = SignalAggregator()
        csvLogger = CSVLogger(this)
        unlockDetector = UnlockDetector(this)
    }

    private fun collectAndSaveSignals() {

        val features = featureExtractor.extractFeatures(windowMinutes = 1)

        val unlocksFromBroadcast = UnlockCounter.unlockCount
        val unlocksFromUsage = try {
            unlockDetector.getUnlockCount()
        } catch (e: Exception) {
            Log.e("PresenceAI", "Error reading unlocks from UsageStats", e)
            0
        }
        val unlocks = unlocksFromBroadcast + unlocksFromUsage

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
            behaviorDrift = features.behaviorDrift,
            timePhase = features.timePhase,
            voiceDetected = voiceDetected,
            proximityDetected = proximityDetected,
            micAllowed = micAllowed,
            bluetoothAllowed = bluetoothAllowed
        )

        SignalRepository.update(signals)

        csvLogger.logSignals(signals)

        Log.d("PresenceAI", "Signals logged: $signals")

        UnlockCounter.unlockCount = 0
        NotificationCounter.notificationCount = 0
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        if (running) return START_STICKY

        running = true

        monitoringThread = Thread {

            while (running) {

                try {

                    collectAndSaveSignals()

                    Thread.sleep(interval)

                } catch (e: Exception) {
                    Log.e("PresenceAI", "Monitoring loop error", e)
                }
            }
        }

        monitoringThread?.start()

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

    override fun onDestroy() {

        MonitoringState.isRunning = false

        running = false

        monitoringThread?.interrupt()

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

//    fun toFeatureVector(): FloatArray {
//        return floatArrayOf(
//            unlocks.toFloat(),
//            microSessions.toFloat(),
//            notificationReflex.toFloat(),
//            behaviorDrift,
//            timePhase.toFloat(),
//            voiceDetected.toFloat(),
//            proximityDetected.toFloat()
//        )
//    }
}