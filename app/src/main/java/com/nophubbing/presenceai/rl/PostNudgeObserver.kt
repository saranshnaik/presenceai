package com.nophubbing.presenceai.rl

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Observes the 45-second window after a nudge fires, computes the reward, updates the
 * [BanditStore], and triggers an optional ML-layer callback with a binary label.
 *
 * **Lifecycle:**
 * 1. [startObservation] is called immediately after the nudge is delivered.
 * 2. The observer registers itself for screen events (caller's responsibility to call
 *    [onScreenEvent] from a BroadcastReceiver or equivalent).
 * 3. After [BanditConfig.OBSERVATION_WINDOW_S] seconds the coroutine wakes up, assembles
 *    the [NudgeObservation], runs [RewardFunction.computeReward], updates [BanditStore],
 *    and invokes [mlCallback] with the appropriate label.
 *
 * **ML callback labels:**
 * | Condition                                    | Label  | Meaning          |
 * |----------------------------------------------|--------|------------------|
 * | Phone down > 45 s AND no re-unlock < 60 s   | `1.0`  | Positive example |
 * | Phone down < 10 s                            | `0.0`  | Negative example |
 * | Anything else                                | `-1.0` | Discard          |
 *
 * @param context     Application context used by [BanditStore].
 * @param scope       [CoroutineScope] whose lifetime should cover the observation window
 *                    (typically the app's process-scope or a service scope).
 * @param mlCallback  Called once after the observation window closes with a label in
 *                    {0.0, 1.0} for usable examples, or -1.0 to signal "discard".
 *                    Invoked on the [scope]'s dispatcher.
 */
class PostNudgeObserver(
    private val context: Context,
    private val scope: CoroutineScope,
    private val mlCallback: (label: Double) -> Unit
) {
    // Mutable observation state — only modified from the scope's coroutine or via
    // synchronized callbacks, but kept simple with @Volatile for this use-case.
    @Volatile private var nudgeTimeMs: Long = 0L
    @Volatile private var nudgeFormat: NudgeFormat = NudgeFormat.HAPTIC
    @Volatile private var wasDismissed: Boolean = false
    @Volatile private var dismissTimeMs: Long? = null
    @Volatile private var reUnlockTimeMs: Long? = null

    private val screenEvents = mutableListOf<ScreenEvent>()
    private val eventLock    = Any()

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Starts the 45-second observation window for a freshly delivered nudge.
     *
     * Resets all accumulated state, then launches a coroutine that sleeps until the window
     * expires and finalises the observation.
     *
     * @param format      The format of the nudge that was just delivered.
     * @param nudgeTimeMs Wall-clock time (ms) when the nudge was delivered.
     */
    fun startObservation(format: NudgeFormat, nudgeTimeMs: Long) {
        // Reset observation state
        this.nudgeTimeMs    = nudgeTimeMs
        this.nudgeFormat    = format
        this.wasDismissed   = false
        this.dismissTimeMs  = null
        this.reUnlockTimeMs = null
        synchronized(eventLock) { screenEvents.clear() }

        scope.launch {
            // Wait for the full observation window
            delay(BanditConfig.OBSERVATION_WINDOW_S * 1_000L)
            finalise()
        }
    }

    /**
     * Records a screen on/off transition during the observation window.
     *
     * Should be called from a BroadcastReceiver listening to
     * [android.content.Intent.ACTION_SCREEN_ON] / [android.content.Intent.ACTION_SCREEN_OFF].
     *
     * Thread-safe: uses [eventLock] so BroadcastReceiver and coroutine threads stay consistent.
     *
     * @param event The [ScreenEvent] to record.
     */
    fun onScreenEvent(event: ScreenEvent) {
        synchronized(eventLock) {
            screenEvents.add(event)
        }

        // Track first re-unlock within the RE_UNLOCK_WINDOW_MS
        if (event.isOn && reUnlockTimeMs == null) {
            val elapsed = event.timestamp - nudgeTimeMs
            if (elapsed in 1 until BanditConfig.RE_UNLOCK_WINDOW_MS) {
                reUnlockTimeMs = event.timestamp
            }
        }
    }

    /**
     * Records a notification dismissal event.
     *
     * Should be called from a [android.service.notification.NotificationListenerService]
     * or equivalent when a PresenceAI notification is removed by the user.
     *
     * Only the **first** dismissal within the observation window is recorded.
     *
     * @param timestampMs Wall-clock time (ms) of the dismissal.
     */
    fun onNotificationDismissed(timestampMs: Long) {
        if (!wasDismissed) {
            wasDismissed  = true
            dismissTimeMs = timestampMs
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    /**
     * Called after the observation window expires.
     *
     * 1. Snapshot screen events (thread-safe).
     * 2. Build [NudgeObservation].
     * 3. Compute reward via [RewardFunction].
     * 4. Update [BanditStore] (load → update → save).
     * 5. Invoke [mlCallback] with the appropriate label.
     */
    private fun finalise() {
        val eventsSnapshot: List<ScreenEvent>
        synchronized(eventLock) {
            eventsSnapshot = screenEvents.toList()
        }

        val obs = NudgeObservation(
            format         = nudgeFormat,
            nudgeTimeMs    = nudgeTimeMs,
            screenEvents   = eventsSnapshot,
            wasDismissed   = wasDismissed,
            dismissTimeMs  = dismissTimeMs,
            reUnlockTimeMs = reUnlockTimeMs
        )

        val reward = RewardFunction.computeReward(obs)

        // Update bandit state in SharedPreferences
        val currentState  = BanditStore.load(context)
        val updatedState  = Bandit.update(currentState, nudgeFormat, reward)
        if (updatedState !== currentState) {  // reference check — avoid pointless write on 0f
            BanditStore.save(context, updatedState)
        }

        // Derive ML label from phone-down duration
        val phoneDownSeconds = RewardFunction.computePhoneDownSeconds(eventsSnapshot, nudgeTimeMs)
        val hasReUnlock = reUnlockTimeMs != null &&
                (reUnlockTimeMs!! - nudgeTimeMs) < BanditConfig.RE_UNLOCK_WINDOW_MS

        val mlLabel: Double = when {
            phoneDownSeconds > BanditConfig.BANDIT_FULL_REWARD_S && !hasReUnlock -> 1.0  // positive
            phoneDownSeconds < 10                                                 -> 0.0  // negative
            else                                                                  -> -1.0 // discard
        }

        mlCallback(mlLabel)
    }
}
