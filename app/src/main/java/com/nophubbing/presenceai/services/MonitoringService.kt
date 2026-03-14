package com.nophubbing.presenceai.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.nophubbing.presenceai.analytics.FeatureExtractor
import com.nophubbing.presenceai.analytics.SignalAggregator
import com.nophubbing.presenceai.analytics.SignalRepository
import com.nophubbing.presenceai.storage.CSVLogger

class MonitoringService : Service() {

    private lateinit var featureExtractor: FeatureExtractor
    private lateinit var signalAggregator: SignalAggregator
    private lateinit var csvLogger: CSVLogger

    private var workerThread: Thread? = null
    private var running = false

    override fun onCreate() {
        super.onCreate()
        Log.d("PresenceAI", "MonitoringService Created")
        featureExtractor = FeatureExtractor(this)
        signalAggregator = SignalAggregator(this)
        csvLogger = CSVLogger(this)
        MonitoringState.isRunning.value = true
    }

    fun collectAndSaveSignals() {
        Log.d("PresenceAI", "Collecting live signals for ML pipeline...")
        
        // Extract the 7 features: x1 to x7
        val features = featureExtractor.extractFeatures(windowMinutes = 10)

        // Generate signals using these features
        val signals = signalAggregator.generateSignals(features)
        
        SignalRepository.update(signals)
        csvLogger.logSignals(signals)
        
        Log.d("PresenceAI", "Signals aggregated and logged: unlock_freq=${features.unlock_freq}, micro_session=${features.micro_session}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (workerThread == null || !workerThread!!.isAlive) {
            running = true
            workerThread = Thread {
                while (running) {
                    try {
                        collectAndSaveSignals()
                        Thread.sleep(60000) // Every minute
                    } catch (e: InterruptedException) {
                        break
                    }
                }
            }
            workerThread?.start()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("PresenceAI", "MonitoringService Destroyed")
        running = false
        workerThread?.interrupt()
        MonitoringState.isRunning.value = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
