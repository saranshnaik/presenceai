package com.nophubbing.presenceai.background

import android.content.Context
import android.util.Log
import com.nophubbing.presenceai.analytics.BehaviorSignals
import com.nophubbing.presenceai.integration.*
import com.nophubbing.presenceai.ml.*
import com.nophubbing.presenceai.rl.Bandit
import com.nophubbing.presenceai.rl.BanditStore
import com.nophubbing.presenceai.signals.*
import kotlinx.coroutines.*
import java.util.Calendar

/**
 * Heartbeat — 30s loop connecting all systems.
 */
class Heartbeat(private val context: Context) {

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private var isRunning = false

    private val nudgeDelivery = NudgeDelivery(context)
    private val nudgeGate = NudgeGate(context)
    private val observer = PostNudgeObserver(context)
    
    // Signals
    private val unlockTracker = UnlockTracker(context)
    private val sessionTracker = SessionTracker(context)
    private val bleScanner = BleScanner(context)
    private val baselineTracker = BaselineTracker(context)

    fun start() {
        if (isRunning) return
        isRunning = true
        scope.launch {
            while (isActive) {
                runCycle()
                delay(30_000L)
            }
        }
    }

    fun stop() {
        isRunning = false
        job.cancel()
    }

    private suspend fun runCycle() = withContext(Dispatchers.IO) {
        try {
            // 1. Gather raw signals
            val unlocksLast10 = unlockTracker.getUnlocksLast10Min()
            val (totalSessions, microSessions) = sessionTracker.getMicroSessionStats(30_000)
            val rollingAvg = 1.0f // Placeholder or calculated from history
            
            // Logic for behavior rate (e.g. unlocks per hour current)
            val currentRate = unlocksLast10 * 6f 
            baselineTracker.updateBaseline(currentRate)

            val row = SignalRow(
                unlocks10Min = unlocksLast10,
                rollingAvgUnlocks = rollingAvg,
                totalSessions = totalSessions,
                microSessions = microSessions,
                lastNotifDeltaMs = System.currentTimeMillis() - NotificationMonitor.lastNotificationTimeMs,
                behaviorRate = currentRate,
                baselineMean = baselineTracker.getMean(),
                baselineStdDev = baselineTracker.getStdDev(),
                baselineReady = baselineTracker.isReady(),
                hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
                voiceActivityDetected = 0, // Placeholder
                peopleNearbyCount = bleScanner.getPeopleNearbyCount()
            )

            // 2. Load model state
            val weights = ModelStore.loadWeights(context)
            val banditStore = BanditStore(context)
            val banditState = banditStore.load()
            
            // Placeholder for analytics display signals
            val signals = BehaviorSignals(
                hourOfDay = row.hourOfDay,
                isEveningSession = if (row.hourOfDay >= 18) 1 else 0,
                unlockCountPerHour = currentRate,
                microSessionRatio = if (totalSessions > 0) microSessions.toFloat() / totalSessions else 0f,
                notifReflexRatio = 0f,
                behaviorDriftScore = row.behaviorRate,
                timePhaseRisk = 0.5f,
                rawUnlocks = row.unlocks10Min
            )

            // 3. Inference Cycle
            val result = InferenceCycle.run(
                signals, 
                row, 
                weights, 
                banditState
            )

            // 4. Delivery Branch
            if (result.format != null && result.copy != null && nudgeGate.canNudge()) {
                withContext(Dispatchers.Main) {
                    nudgeDelivery.deliver(result.format, result.copy)
                    nudgeGate.recordNudge()
                    
                    // 5. Shared observation window
                    observer.start(result.format) { resolved ->
                        // Online Learning Step
                        scope.launch(Dispatchers.IO) {
                            val fv = FeatureEngineering.buildFeatureVector(row).map { it.toDouble() }
                            val newWeights = OnlineLearner.update(
                                fv,
                                resolved.lrLabel,
                                weights
                            )
                            
                            // Save updated weights
                            ModelStore.saveWeights(context, newWeights)
                            
                            // Bandit update
                            val newBanditState = Bandit.update(banditState, resolved.format, resolved.banditReward)
                            banditStore.save(newBanditState)
                        }
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("Heartbeat", "Cycle failed: ${e.message}")
        }
    }
}
