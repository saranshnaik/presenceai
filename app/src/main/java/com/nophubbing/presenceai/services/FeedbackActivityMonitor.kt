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
 *
 * This makes the system resilient to dishonest or lazy feedback:
 *  - User says "not phubbing" but unlocks 4 times in 20 s → label = phubbing
 *  - User says "was phubbing" but phone untouched for 20 s → label = not phubbing
 */
class FeedbackActivityMonitor private constructor(
    private val onlineLearner: OnlineLearnerPort,       // routes back to PipelineRunner
    private val signalProvider: SignalProvider
) {

    // ── Singleton ─────────────────────────────────────────────────────────────

    companion object {
        private const val TAG = "FeedbackActivityMonitor"

        /** Observation window in milliseconds after feedback is received. */
        private const val OBSERVATION_WINDOW_MS = 20_000L

        /** Snapshot interval within the observation window. */
        private const val SNAPSHOT_INTERVAL_MS  = 2_000L

        /** Number of snapshots collected (20s / 2s = 10). */
        private const val SNAPSHOT_COUNT = (OBSERVATION_WINDOW_MS / SNAPSHOT_INTERVAL_MS).toInt()

        // Thresholds that determine activity-derived label
        private const val HIGH_UNLOCK_RATE   = 2   // unlocks per 20 s → definitely phubbing
        private const val HIGH_SESSION_COUNT  = 3   // micro-sessions per 20 s
        private const val LOW_PRESENCE_SCORE  = 45f // below this in final snapshot → drifting

        @Volatile private var instance: FeedbackActivityMonitor? = null

        fun getInstance(): FeedbackActivityMonitor? = instance

        fun init(learner: OnlineLearnerPort, provider: SignalProvider): FeedbackActivityMonitor {
            return instance ?: synchronized(this) {
                instance ?: FeedbackActivityMonitor(learner, provider).also { instance = it }
            }
        }
    }

    // ── State ─────────────────────────────────────────────────────────────────

    data class MonitorState(
        val isObserving: Boolean   = false,
        val secondsLeft: Int       = 0,
        val finalLabel: Boolean?   = null,   // null = not yet determined
        val confidence: Float      = 0f
    )

    private val _state = MutableStateFlow(MonitorState())
    val state: StateFlow<MonitorState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var observationJob: Job? = null

    // Snapshots collected during the observation window
    data class SignalSnapshot(
        val unlockCount   : Int,
        val sessionCount  : Int,
        val presenceScore : Float,
        val notifReflexes : Int,
        val features      : FloatArray    // raw feature vector from FeatureEngineering
    )

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Entry point — called by NudgeFeedbackReceiver when the user responds to a nudge.
     *
     * @param userClaimsPhubbing  true if user tapped "I was phubbing"
     * @param scoreAtNudge        the phubbing score that triggered the nudge
     */
    fun onUserFeedback(userClaimsPhubbing: Boolean, scoreAtNudge: Float) {
        Log.d(TAG, "Feedback received: user=$userClaimsPhubbing scoreAtNudge=$scoreAtNudge")

        // Cancel any in-progress observation
        observationJob?.cancel()

        observationJob = scope.launch {
            runObservationWindow(
                userClaimsPhubbing = userClaimsPhubbing,
                scoreAtNudge       = scoreAtNudge
            )
        }
    }

    fun destroy() {
        scope.cancel()
        instance = null
    }

    // ── Observation window logic ───────────────────────────────────────────────

    private suspend fun runObservationWindow(
        userClaimsPhubbing: Boolean,
        scoreAtNudge: Float
    ) {
        val snapshots = mutableListOf<SignalSnapshot>()

        _state.value = MonitorState(isObserving = true, secondsLeft = (OBSERVATION_WINDOW_MS / 1000).toInt())

        Log.d(TAG, "Starting ${OBSERVATION_WINDOW_MS / 1000}s observation window…")

        // Collect snapshots every SNAPSHOT_INTERVAL_MS
        repeat(SNAPSHOT_COUNT) { i ->
            delay(SNAPSHOT_INTERVAL_MS)

            val snap = signalProvider.captureSnapshot()
            snapshots.add(snap)

            val remaining = ((SNAPSHOT_COUNT - i - 1) * SNAPSHOT_INTERVAL_MS / 1000).toInt()
            _state.value = _state.value.copy(secondsLeft = remaining)

            Log.d(TAG, "Snapshot ${i + 1}/$SNAPSHOT_COUNT — unlocks=${snap.unlockCount} sessions=${snap.sessionCount} score=${snap.presenceScore}")
        }

        // ── Derive activity-based label ───────────────────────────────────────
        val activityLabel = deriveActivityLabel(snapshots)
        Log.d(TAG, "Activity label: $activityLabel  |  User claimed: $userClaimsPhubbing")

        // ── Merge labels ──────────────────────────────────────────────────────
        val (finalLabel, confidence) = mergeLabels(
            userLabel     = userClaimsPhubbing,
            activityLabel = activityLabel,
            snapshots     = snapshots
        )

        Log.d(TAG, "Final label: $finalLabel  confidence=$confidence")

        _state.value = MonitorState(
            isObserving = false,
            secondsLeft = 0,
            finalLabel  = finalLabel,
            confidence  = confidence
        )

        // ── Update model weights ──────────────────────────────────────────────
        val representativeFeatures = snapshots.last().features   // use end-of-window features
        updateModelWeights(finalLabel, representativeFeatures, confidence)
    }

    // ── Label derivation ──────────────────────────────────────────────────────

    /**
     * Looks at all snapshots and decides whether the sensor data says the user
     * was actually phubbing, independent of what they reported.
     */
    private fun deriveActivityLabel(snapshots: List<SignalSnapshot>): Boolean {
        if (snapshots.isEmpty()) return false

        val totalUnlocks  = snapshots.sumOf { it.unlockCount }
        val totalSessions = snapshots.sumOf { it.sessionCount }
        val avgScore      = snapshots.map { it.presenceScore }.average().toFloat()
        val totalReflexes = snapshots.sumOf { it.notifReflexes }

        // Score evidence points
        var evidence = 0
        if (totalUnlocks  >= HIGH_UNLOCK_RATE)  evidence++
        if (totalSessions >= HIGH_SESSION_COUNT) evidence++
        if (avgScore      <= LOW_PRESENCE_SCORE) evidence++
        if (totalReflexes >= 1)                  evidence++

        Log.d(TAG, "Activity evidence score: $evidence/4 (unlocks=$totalUnlocks sessions=$totalSessions avgScore=$avgScore reflexes=$totalReflexes)")

        // Majority rules: 2+ indicators → phubbing
        return evidence >= 2
    }

    /**
     * Merges user self-report with the activity-derived label.
     *
     * Agreement   → high confidence, use either label
     * Disagreement→ activity label wins (sensor data beats self-report),
     *               but confidence is reduced
     */
    private fun mergeLabels(
        userLabel     : Boolean,
        activityLabel : Boolean,
        snapshots     : List<SignalSnapshot>
    ): Pair<Boolean, Float> {

        return if (userLabel == activityLabel) {
            // Full agreement
            Pair(activityLabel, 0.90f)
        } else {
            // Conflict: trust the sensors more, but with lower confidence
            Log.w(TAG, "Label conflict — user=$userLabel activity=$activityLabel → trusting activity")
            Pair(activityLabel, 0.60f)
        }
    }

    // ── Weight update ─────────────────────────────────────────────────────────

    /**
     * Calls OnlineLearner.update() with the final validated label.
     * Skips update if confidence is too low to be trustworthy.
     */
    private fun updateModelWeights(
        isPhubbing : Boolean,
        features   : FloatArray,
        confidence : Float
    ) {
        if (confidence < 0.50f) {
            Log.w(TAG, "Confidence too low ($confidence) — skipping weight update")
            return
        }

        val label = if (isPhubbing) 1 else 0
        Log.d(TAG, "Updating model: label=$label confidence=$confidence")

        try {
            // OnlineLearner.update() takes (features, label) — adjust to your actual signature
            onlineLearner.update(features, label)
            Log.d(TAG, "Model weights updated successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update model: ${e.message}")
        }
    }
}

// ── SignalProvider interface ───────────────────────────────────────────────────

/**
 * Abstraction over your FeatureEngineering / sensor layer.
 * Implement this interface in your existing pipeline and inject it into
 * FeedbackActivityMonitor.
 */
interface SignalProvider {
    /**
     * Capture the current sensor snapshot.
     * Called on a background thread every ~2 seconds during observation.
     */
    suspend fun captureSnapshot(): FeedbackActivityMonitor.SignalSnapshot
}
