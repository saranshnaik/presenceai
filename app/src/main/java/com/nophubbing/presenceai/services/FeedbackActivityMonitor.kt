package com.nophubbing.presenceai.services

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * FeedbackActivityMonitor
 *
 * Triggered when user taps "Yes, I was distracted" or "No, false alarm" on the nudge notification.
 *
 * Steps:
 *   1. Record user's self-report (isPhubbing).
 *   2. Open a 20s observation window, collect signal snapshots.
 *   3. Derive an activity label from those snapshots.
 *   4. Merge user report + activity label → final ground-truth.
 *   5. Call onlineLearner.update() → LR weight update.
 *   6. Call rewardCallback(reward, format) → Bandit reward update in ViewModel.
 *
 * Both LR and Bandit are updated from button-tap feedback, so rewards
 * accumulate even when the phone screen doesn't go off during the demo.
 */
class FeedbackActivityMonitor private constructor(
    private val onlineLearner: OnlineLearnerPort,
    private val signalProvider: SignalProvider,
    private val rewardCallback: ((reward: Float) -> Unit)? = null
) {

    companion object {
        private const val TAG = "FeedbackActivityMonitor"

        private const val OBSERVATION_WINDOW_MS = 20_000L
        private const val SNAPSHOT_INTERVAL_MS  =  2_000L
        private const val SNAPSHOT_COUNT = (OBSERVATION_WINDOW_MS / SNAPSHOT_INTERVAL_MS).toInt()

        private const val HIGH_UNLOCK_RATE    = 2
        private const val HIGH_SESSION_COUNT  = 3
        private const val HIGH_PHUBBING_SCORE = 60f

        @Volatile private var instance: FeedbackActivityMonitor? = null

        fun getInstance(): FeedbackActivityMonitor? = instance

        /** Call once from ViewModel with both learner and reward callback. */
        fun init(
            learner: OnlineLearnerPort,
            provider: SignalProvider,
            onReward: ((reward: Float) -> Unit)? = null
        ): FeedbackActivityMonitor {
            val new = FeedbackActivityMonitor(learner, provider, onReward)
            instance = new
            return new
        }
    }

    data class MonitorState(
        val isObserving: Boolean = false,
        val secondsLeft: Int     = 0,
        val finalLabel: Boolean? = null,
        val confidence: Float    = 0f
    )

    private val _state = MutableStateFlow(MonitorState())
    val state: StateFlow<MonitorState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var observationJob: Job? = null

    // Set by ViewModel after a nudge fires so we know which format to reward
    @Volatile var lastNudgeFormat: com.nophubbing.presenceai.rl.NudgeFormat? = null

    data class SignalSnapshot(
        val unlockCount   : Int,
        val sessionCount  : Int,
        val phubbingScore : Float,
        val notifReflexes : Int,
        val features      : FloatArray
    )

    fun onUserFeedback(userClaimsPhubbing: Boolean, scoreAtNudge: Float) {
        Log.d(TAG, "Feedback: user=$userClaimsPhubbing scoreAtNudge=$scoreAtNudge format=$lastNudgeFormat")
        observationJob?.cancel()
        observationJob = scope.launch { runWindow(userClaimsPhubbing, scoreAtNudge) }
    }

    fun destroy() {
        scope.cancel()
        instance = null
    }

    // ── Observation window ────────────────────────────────────────────────────

    private suspend fun runWindow(userClaimsPhubbing: Boolean, scoreAtNudge: Float) {
        val snapshots = mutableListOf<SignalSnapshot>()
        _state.value = MonitorState(isObserving = true, secondsLeft = (OBSERVATION_WINDOW_MS / 1000).toInt())

        repeat(SNAPSHOT_COUNT) { i ->
            delay(SNAPSHOT_INTERVAL_MS)
            snapshots.add(signalProvider.captureSnapshot())
            val remaining = ((SNAPSHOT_COUNT - i - 1) * SNAPSHOT_INTERVAL_MS / 1000).toInt()
            _state.value = _state.value.copy(secondsLeft = remaining)
        }

        val activityLabel           = deriveActivityLabel(snapshots)
        val (finalLabel, confidence) = mergeLabels(userClaimsPhubbing, activityLabel)

        _state.value = MonitorState(false, 0, finalLabel, confidence)

        // 1. LR update
        updateLR(finalLabel, snapshots.last().features, confidence)

        // 2. Bandit reward — computed from user feedback immediately
        // Don't wait for the 45s PostNudgeObserver screen watch during demos
        val reward = computeRewardFromFeedback(userClaimsPhubbing, finalLabel, scoreAtNudge)
        Log.d(TAG, "Reward from feedback: $reward (user=$userClaimsPhubbing activity=$activityLabel)")

        rewardCallback?.invoke(reward)
    }

    // ── Label helpers ─────────────────────────────────────────────────────────

    private fun deriveActivityLabel(snapshots: List<SignalSnapshot>): Boolean {
        if (snapshots.isEmpty()) return false
        var evidence = 0
        if (snapshots.sumOf { it.unlockCount }  >= HIGH_UNLOCK_RATE)   evidence++
        if (snapshots.sumOf { it.sessionCount } >= HIGH_SESSION_COUNT)  evidence++
        if (snapshots.map { it.phubbingScore }.average() >= HIGH_PHUBBING_SCORE) evidence++
        if (snapshots.sumOf { it.notifReflexes } >= 1)                 evidence++
        return evidence >= 2
    }

    private fun mergeLabels(user: Boolean, activity: Boolean): Pair<Boolean, Float> =
        if (user == activity) Pair(activity, 0.90f)
        else { Log.w(TAG, "Label conflict user=$user activity=$activity → trust activity"); Pair(activity, 0.60f) }

    /**
     * Reward from explicit user feedback (used instead of 45s screen-watch for demos):
     *
     *  User said YES (was phubbing) + high phub score  → +1.0 (nudge was right, worked)
     *  User said YES + moderate score                   → +0.5
     *  User said NO  (false alarm)                      → -0.5 (nudge was wrong)
     *  User said NO  + score was very high (ML sure)    → -1.0 (nudge right but user denying)
     */
    private fun computeRewardFromFeedback(userYes: Boolean, activityLabel: Boolean, score: Float): Float {
        return when {
            userYes && score >= 0.80f  -> +1.0f
            userYes && score >= 0.50f  -> +0.5f
            userYes                    -> +0.3f
            !userYes && score >= 0.80f -> -1.0f  // very high confidence, user denied → big negative
            !userYes                   -> -0.5f
            else                       ->  0.0f
        }
    }

    private fun updateLR(isPhubbing: Boolean, features: FloatArray, confidence: Float) {
        if (confidence < 0.50f) { Log.w(TAG, "Low confidence $confidence — skipping LR update"); return }
        try {
            onlineLearner.update(features, if (isPhubbing) 1 else 0)
            Log.d(TAG, "✅ LR update: label=$isPhubbing confidence=$confidence")
        } catch (e: Exception) {
            Log.e(TAG, "LR update failed: ${e.message}")
        }
    }
}

interface SignalProvider {
    suspend fun captureSnapshot(): FeedbackActivityMonitor.SignalSnapshot
}
