package com.nophubbing.presenceai.services

import android.util.Log
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.coroutineContext

/**
 * FalseNegativeGuard
 *
 * Addresses the case where the model NEVER fires a nudge even though the user
 * is clearly phubbing (silent false negatives).
 */
class FalseNegativeGuard(
    private val onlineLearner  : OnlineLearnerPort,
    private val signalProvider : SignalProvider
) {

    companion object {
        private const val TAG = "FalseNegativeGuard"

        private const val SILENCE_WINDOW_MS = 3 * 60 * 1_000L
        private const val VALIDATION_MS     = 20_000L
        private const val SUSPICION_THRESHOLD = 40f
        private const val SILENT_PHUBBING_EVIDENCE = 2
        private const val SNAPSHOT_INTERVAL_MS = 2_000L
        private const val SNAPSHOT_COUNT = (VALIDATION_MS / SNAPSHOT_INTERVAL_MS).toInt()
        private const val MIN_CONFIDENCE = 0.65f
    }

    enum class GuardStatus {
        IDLE, WATCHING, VALIDATING, CORRECTED
    }

    private val _status = MutableStateFlow(GuardStatus.IDLE)
    val status: StateFlow<GuardStatus> = _status.asStateFlow()

    private val _correctionCount = MutableStateFlow(0)
    val correctionCount: StateFlow<Int> = _correctionCount.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile private var lastNudgeTimestamp = System.currentTimeMillis()
    @Volatile private var lastHighScoreTimestamp = System.currentTimeMillis()
    @Volatile private var socialPresent = false

    private var validationJob: Job? = null
    private var watchJob: Job? = null

    @VisibleForTesting
    fun triggerSilentValidationForTest() {
        triggerSilentValidation()
    }

    fun start() {
        Log.d(TAG, "FalseNegativeGuard started")
        watchJob = scope.launch { runWatchLoop() }
    }

    fun stop() {
        watchJob?.cancel()
        validationJob?.cancel()
        _status.value = GuardStatus.IDLE
    }

    fun destroy() {
        stop()
        scope.cancel()
    }

    fun onScoreUpdate(score: Float, social: Boolean) {
        socialPresent = social
        // If score is high, the model is active or recently nudged
        if (score >= 60f) { 
            lastHighScoreTimestamp = System.currentTimeMillis()
        }
    }

    fun onNudgeFired() {
        lastNudgeTimestamp = System.currentTimeMillis()
        lastHighScoreTimestamp = System.currentTimeMillis()
    }

    private suspend fun runWatchLoop() {
        while (coroutineContext.isActive) {
            delay(30_000L)
            if (_status.value == GuardStatus.VALIDATING) continue

            val now = System.currentTimeMillis()
            val silentFor = now - maxOf(lastNudgeTimestamp, lastHighScoreTimestamp)

            if (socialPresent && silentFor >= SILENCE_WINDOW_MS) {
                Log.d(TAG, "Silence detected for ${silentFor / 1000}s with social presence")
                _status.value = GuardStatus.WATCHING
                triggerSilentValidation()
            }
        }
    }

    private fun triggerSilentValidation() {
        validationJob?.cancel()
        validationJob = scope.launch {
            _status.value = GuardStatus.VALIDATING
            val snapshots = mutableListOf<FeedbackActivityMonitor.SignalSnapshot>()

            repeat(SNAPSHOT_COUNT) {
                delay(SNAPSHOT_INTERVAL_MS)
                snapshots.add(signalProvider.captureSnapshot())
            }

            val (isPhubbing, confidence) = analyzeSnapshots(snapshots)

            if (confidence >= MIN_CONFIDENCE) {
                injectTrainingExample(isPhubbing, snapshots.last().features, confidence)
                _status.value = GuardStatus.CORRECTED
                _correctionCount.value++
                lastNudgeTimestamp = System.currentTimeMillis()
                lastHighScoreTimestamp = System.currentTimeMillis()
            } else {
                _status.value = GuardStatus.IDLE
            }
        }
    }

    private fun analyzeSnapshots(
        snapshots: List<FeedbackActivityMonitor.SignalSnapshot>
    ): Pair<Boolean, Float> {
        if (snapshots.isEmpty()) return Pair(false, 0f)

        val totalUnlocks  = snapshots.sumOf { it.unlockCount }
        val totalSessions = snapshots.sumOf { it.sessionCount }
        val avgScore      = snapshots.map { it.phubbingScore }.average().toFloat()
        val totalReflexes = snapshots.sumOf { it.notifReflexes }

        var evidence = 0
        if (totalUnlocks  >= 2)    evidence++
        if (totalSessions >= 3)    evidence++
        if (avgScore      <= SUSPICION_THRESHOLD)  evidence++ // Evidence of false negative if score is low
        if (totalReflexes >= 1)    evidence++

        val isPhubbing = evidence >= SILENT_PHUBBING_EVIDENCE
        val confidence = when (evidence) {
            4    -> 0.90f
            3    -> 0.75f
            2    -> MIN_CONFIDENCE
            else -> 0.30f
        }
        return Pair(isPhubbing, confidence)
    }

    private fun injectTrainingExample(isPhubbing: Boolean, features: FloatArray, confidence: Float) {
        val label = if (isPhubbing) 1 else 0
        try {
            onlineLearner.update(features, label)
        } catch (e: Exception) {
            Log.e(TAG, "Silent weight update failed: ${e.message}")
        }
    }
}
