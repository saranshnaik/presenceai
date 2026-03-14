package com.nophubbing.presenceai.services

import android.util.Log
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * FalseNegativeGuard
 *
 * Addresses the case where the model NEVER fires a nudge even though the user
 * is clearly phubbing (silent false negatives).  Without this module, the model
 * would never receive positive training examples and would drift toward always
 * predicting "not phubbing."
 *
 * Strategy
 * ────────
 * 1. Keep a rolling window of the last N score readings.
 * 2. If the average score stays BELOW the threshold for a sustained period
 *    (SILENCE_WINDOW_MS) while social presence is confirmed, the model may be
 *    under-detecting.
 * 3. Silently observe the raw signal for VALIDATION_MS to generate a ground-
 *    truth label WITHOUT disturbing the user.
 * 4. If the silent observation confirms phubbing, inject a positive training
 *    example directly into OnlineLearner — no notification fired.
 * 5. If it confirms attentive behaviour, inject a negative example to reinforce
 *    correct low scores.
 *
 * This is a background, user-invisible correction loop.
 */
class FalseNegativeGuard(
    private val onlineLearner  : OnlineLearnerPort,    // routes back to PipelineRunner
    private val signalProvider : SignalProvider
) {

    companion object {
        private const val TAG = "FalseNegativeGuard"

        /** Consecutive low-score time before we suspect false negatives (ms). */
        private const val SILENCE_WINDOW_MS = 3 * 60 * 1_000L   // 3 minutes

        /** Silent validation window after suspicion is triggered (ms). */
        private const val VALIDATION_MS     = 20_000L

        /** Score must stay below this to be "suspiciously low". */
        private const val SUSPICION_THRESHOLD = 40f

        /** Activity evidence threshold within the silent validation window. */
        private const val SILENT_PHUBBING_EVIDENCE = 2   // out of 4

        /** Snapshot interval inside validation window (ms). */
        private const val SNAPSHOT_INTERVAL_MS = 2_000L
        private const val SNAPSHOT_COUNT = (VALIDATION_MS / SNAPSHOT_INTERVAL_MS).toInt()

        /** Min confidence to inject a training example. */
        private const val MIN_CONFIDENCE = 0.65f
    }

    // ── State ─────────────────────────────────────────────────────────────────

    enum class GuardStatus {
        IDLE,           // Normal — model is nudging as expected
        WATCHING,       // Model hasn't nudged in a while — watching silently
        VALIDATING,     // In the 20-second silent observation window
        CORRECTED       // Injected a training example this cycle
    }

    private val _status = MutableStateFlow(GuardStatus.IDLE)
    val status: StateFlow<GuardStatus> = _status.asStateFlow()

    /** Running count of silent corrections applied this session. */
    private val _correctionCount = MutableStateFlow(0)
    val correctionCount: StateFlow<Int> = _correctionCount.asStateFlow()

    // ── Private ────────────────────────────────────────────────────────────────

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Timestamp of the last nudge delivered by NudgingSystem. */
    @Volatile private var lastNudgeTimestamp = System.currentTimeMillis()

    /** Timestamp of the last score update that was ABOVE threshold. */
    @Volatile private var lastHighScoreTimestamp = System.currentTimeMillis()

    /** Whether social presence was confirmed in the last score update. */
    @Volatile private var socialPresent = false

    private var validationJob: Job? = null

    // Periodic check job
    private var watchJob: Job? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Test hook — skips the 3-minute silence wait and triggers validation immediately. */
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
        Log.d(TAG, "FalseNegativeGuard stopped")
    }

    fun destroy() {
        stop()
        scope.cancel()
    }

    // ── Feed score updates ────────────────────────────────────────────────────

    /**
     * Called by the same pipeline that feeds NudgingSystem.
     * Must be called for every score update so the guard can track silence.
     */
    fun onScoreUpdate(score: Float, social: Boolean) {
        socialPresent = social

        if (score >= NudgingSystem.DEFAULT_THRESHOLD) {
            lastHighScoreTimestamp = System.currentTimeMillis()
        }
    }

    /** Notify guard that NudgingSystem fired a real nudge (resets silence timer). */
    fun onNudgeFired() {
        lastNudgeTimestamp = System.currentTimeMillis()
        lastHighScoreTimestamp = System.currentTimeMillis()
        Log.d(TAG, "Nudge fired — resetting silence timers")
    }

    // ── Watch loop ────────────────────────────────────────────────────────────

    /**
     * Periodically checks whether the model has been suspiciously quiet
     * while social presence is active.
     */
    private suspend fun runWatchLoop() {
        while (isActive) {
            delay(30_000L)   // check every 30 seconds

            if (_status.value == GuardStatus.VALIDATING) continue  // already validating

            val now = System.currentTimeMillis()
            val silentFor = now - maxOf(lastNudgeTimestamp, lastHighScoreTimestamp)

            if (socialPresent && silentFor >= SILENCE_WINDOW_MS) {
                Log.d(TAG, "Silence detected for ${silentFor / 1000}s with social presence — triggering silent validation")
                _status.value = GuardStatus.WATCHING
                triggerSilentValidation()
            }
        }
    }

    // ── Silent validation ─────────────────────────────────────────────────────

    private fun triggerSilentValidation() {
        validationJob?.cancel()
        validationJob = scope.launch {
            _status.value = GuardStatus.VALIDATING
            Log.d(TAG, "Silent validation window opened (${VALIDATION_MS / 1000}s)")

            val snapshots = mutableListOf<FeedbackActivityMonitor.SignalSnapshot>()

            repeat(SNAPSHOT_COUNT) {
                delay(SNAPSHOT_INTERVAL_MS)
                snapshots.add(signalProvider.captureSnapshot())
            }

            val (isPhubbing, confidence) = analyzeSnapshots(snapshots)

            Log.d(TAG, "Silent validation result: isPhubbing=$isPhubbing confidence=$confidence")

            if (confidence >= MIN_CONFIDENCE) {
                injectTrainingExample(isPhubbing, snapshots.last().features, confidence)
                _status.value = GuardStatus.CORRECTED
                _correctionCount.value++

                // Reset timers so we don't immediately re-trigger
                lastNudgeTimestamp     = System.currentTimeMillis()
                lastHighScoreTimestamp = System.currentTimeMillis()
            } else {
                Log.d(TAG, "Confidence too low for silent correction — staying in IDLE")
                _status.value = GuardStatus.IDLE
            }
        }
    }

    // ── Analysis ──────────────────────────────────────────────────────────────

    /**
     * Analyse snapshots without user interaction — mirrors FeedbackActivityMonitor
     * but is entirely silent.
     */
    private fun analyzeSnapshots(
        snapshots: List<FeedbackActivityMonitor.SignalSnapshot>
    ): Pair<Boolean, Float> {

        if (snapshots.isEmpty()) return Pair(false, 0f)

        val totalUnlocks  = snapshots.sumOf { it.unlockCount }
        val totalSessions = snapshots.sumOf { it.sessionCount }
        val avgScore      = snapshots.map { it.presenceScore }.average().toFloat()
        val totalReflexes = snapshots.sumOf { it.notifReflexes }

        var evidence = 0
        if (totalUnlocks  >= 2)    evidence++
        if (totalSessions >= 3)    evidence++
        if (avgScore      <= 40f)  evidence++
        if (totalReflexes >= 1)    evidence++

        val isPhubbing = evidence >= SILENT_PHUBBING_EVIDENCE

        // Confidence scales with how strongly the evidence speaks
        val confidence = when (evidence) {
            4    -> 0.90f
            3    -> 0.75f
            2    -> MIN_CONFIDENCE
            else -> 0.30f    // insufficient
        }

        Log.d(TAG, "Silent analysis: evidence=$evidence/4 isPhubbing=$isPhubbing confidence=$confidence")
        return Pair(isPhubbing, confidence)
    }

    // ── Weight update ─────────────────────────────────────────────────────────

    private fun injectTrainingExample(
        isPhubbing : Boolean,
        features   : FloatArray,
        confidence : Float
    ) {
        val label = if (isPhubbing) 1 else 0
        Log.d(TAG, "Injecting silent training example: label=$label confidence=$confidence")

        try {
            onlineLearner.update(features, label)
            Log.d(TAG, "Silent weight update applied successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Silent weight update failed: ${e.message}")
        }
    }
}
