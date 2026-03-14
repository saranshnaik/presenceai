package com.nophubbing.presenceai.services

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * FeedbackActivityMonitor
 *
 * After the user taps Accept or Dismiss on a nudge notification, this module:
 *
 *  1. Records the user's self-reported feedback (isPhubbing = true/false).
 *  2. Opens a 20-second observation window and collects real-time signal snapshots.
 *  3. Computes an **activity-derived label** from those snapshots.
 *  4. Merges the self-report and the activity label into a final ground-truth label.
 *  5. Calls OnlineLearner to update model weights.
 */
class FeedbackActivityMonitor private constructor(
    private val onlineLearner: OnlineLearnerPort,
    private val signalProvider: SignalProvider
) {

    companion object {
        private const val TAG = "FeedbackActivityMonitor"

        private const val OBSERVATION_WINDOW_MS = 20_000L
        private const val SNAPSHOT_INTERVAL_MS  = 2_000L
        private const val SNAPSHOT_COUNT = (OBSERVATION_WINDOW_MS / SNAPSHOT_INTERVAL_MS).toInt()

        // Thresholds that determine activity-derived label (assuming phubbingScore 0-100)
        private const val HIGH_UNLOCK_RATE    = 2   
        private const val HIGH_SESSION_COUNT   = 3   
        private const val HIGH_PHUBBING_SCORE  = 60f 

        @Volatile private var instance: FeedbackActivityMonitor? = null

        fun getInstance(): FeedbackActivityMonitor? = instance

        fun init(learner: OnlineLearnerPort, provider: SignalProvider): FeedbackActivityMonitor {
            return instance ?: synchronized(this) {
                instance ?: FeedbackActivityMonitor(learner, provider).also { instance = it }
            }
        }
    }

    data class MonitorState(
        val isObserving: Boolean   = false,
        val secondsLeft: Int       = 0,
        val finalLabel: Boolean?   = null,
        val confidence: Float      = 0f
    )

    private val _state = MutableStateFlow(MonitorState())
    val state: StateFlow<MonitorState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var observationJob: Job? = null

    data class SignalSnapshot(
        val unlockCount   : Int,
        val sessionCount  : Int,
        val phubbingScore : Float,
        val notifReflexes : Int,
        val features      : FloatArray    
    )

    fun onUserFeedback(userClaimsPhubbing: Boolean, scoreAtNudge: Float) {
        Log.d(TAG, "Feedback received: user=$userClaimsPhubbing scoreAtNudge=$scoreAtNudge")
        observationJob?.cancel()
        observationJob = scope.launch {
            runObservationWindow(userClaimsPhubbing, scoreAtNudge)
        }
    }

    fun destroy() {
        scope.cancel()
        instance = null
    }

    private suspend fun runObservationWindow(
        userClaimsPhubbing: Boolean,
        scoreAtNudge: Float
    ) {
        val snapshots = mutableListOf<SignalSnapshot>()
        _state.value = MonitorState(isObserving = true, secondsLeft = (OBSERVATION_WINDOW_MS / 1000).toInt())

        repeat(SNAPSHOT_COUNT) { i ->
            delay(SNAPSHOT_INTERVAL_MS)
            val snap = signalProvider.captureSnapshot()
            snapshots.add(snap)
            val remaining = ((SNAPSHOT_COUNT - i - 1) * SNAPSHOT_INTERVAL_MS / 1000).toInt()
            _state.value = _state.value.copy(secondsLeft = remaining)
        }

        val activityLabel = deriveActivityLabel(snapshots)
        val (finalLabel, confidence) = mergeLabels(userClaimsPhubbing, activityLabel)

        _state.value = MonitorState(false, 0, finalLabel, confidence)
        updateModelWeights(finalLabel, snapshots.last().features, confidence)
    }

    private fun deriveActivityLabel(snapshots: List<SignalSnapshot>): Boolean {
        if (snapshots.isEmpty()) return false

        val totalUnlocks  = snapshots.sumOf { it.unlockCount }
        val totalSessions = snapshots.sumOf { it.sessionCount }
        val avgScore      = snapshots.map { it.phubbingScore }.average().toFloat()
        val totalReflexes = snapshots.sumOf { it.notifReflexes }

        var evidence = 0
        if (totalUnlocks  >= HIGH_UNLOCK_RATE)   evidence++
        if (totalSessions >= HIGH_SESSION_COUNT)  evidence++
        if (avgScore      >= HIGH_PHUBBING_SCORE) evidence++
        if (totalReflexes >= 1)                   evidence++

        return evidence >= 2
    }

    private fun mergeLabels(userLabel: Boolean, activityLabel: Boolean): Pair<Boolean, Float> {
        return if (userLabel == activityLabel) {
            Pair(activityLabel, 0.90f)
        } else {
            Log.w(TAG, "Label conflict — user=$userLabel activity=$activityLabel → trusting activity")
            Pair(activityLabel, 0.60f)
        }
    }

    private fun updateModelWeights(isPhubbing: Boolean, features: FloatArray, confidence: Float) {
        if (confidence < 0.50f) return
        val label = if (isPhubbing) 1 else 0
        try {
            onlineLearner.update(features, label)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update model: ${e.message}")
        }
    }
}

interface SignalProvider {
    suspend fun captureSnapshot(): FeedbackActivityMonitor.SignalSnapshot
}
