package com.nophubbing.presenceai.integration

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.nophubbing.presenceai.ml.PipelineConfig
import com.nophubbing.presenceai.rl.LabelResolver
import com.nophubbing.presenceai.rl.NudgeFormat
import com.nophubbing.presenceai.rl.ResolvedLabels
import kotlinx.coroutines.*

/**
 * PostNudgeObserver — monitors screen events for 45s after a nudge fires.
 *
 * Observation logic (all thresholds from PipelineConfig):
 *   - If phone screen goes OFF within the window and stays off ≥ 45s → label = 1.0 (responded)
 *   - If phone screen goes OFF 20–44s                               → label = -1.0 (partial, ambiguous)
 *   - If phone stays ON the entire 45s window                       → label = 0.0 (ignored)
 *   - If notification was dismissed in < 3s                         → label = 0.0, reward = -0.5 or -1.0
 *
 * How to use:
 *   1. Call start(format, onResolved) immediately after delivering a nudge.
 *   2. onResolved receives ResolvedLabels with lrLabel + banditReward.
 *   3. Pass those to ViewModel.onLabelResolved().
 *   4. Observer auto-cancels after the window ends.
 *
 * Only one observer runs at a time — calling start() while one is active
 * cancels the previous one first.
 */
class PostNudgeObserver(private val context: Context) {

    private val config = PipelineConfig()
    private var job: Job? = null
    private var screenReceiver: BroadcastReceiver? = null

    // Observed events during the window
    @Volatile private var screenOffTimeMs: Long = -1L
    @Volatile private var reUnlockTimeMs: Long  = -1L
    @Volatile private var notifDismissMs: Long  = -1L

    /**
     * Start a 45-second observation window.
     *
     * @param format      Which nudge format was delivered (HAPTIC or NOTIFICATION)
     * @param onResolved  Callback fired once with the resolved labels — always called,
     *                    even on timeout or cancellation.
     */
    fun start(
        format: NudgeFormat,
        onResolved: (ResolvedLabels) -> Unit
    ) {
        stop()  // Cancel any existing observer

        val nudgeFiredAt = System.currentTimeMillis()
        screenOffTimeMs  = -1L
        reUnlockTimeMs   = -1L
        notifDismissMs   = -1L

        // Register screen ON/OFF receiver
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val now = System.currentTimeMillis()
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        if (screenOffTimeMs < 0) {
                            screenOffTimeMs = now
                            Log.d("PresenceAI", "PostNudgeObserver: screen OFF at +${now - nudgeFiredAt}ms")
                        }
                    }
                    Intent.ACTION_USER_PRESENT,   // unlock
                    Intent.ACTION_SCREEN_ON -> {
                        if (screenOffTimeMs > 0 && reUnlockTimeMs < 0) {
                            reUnlockTimeMs = now
                            Log.d("PresenceAI", "PostNudgeObserver: re-unlock at +${now - nudgeFiredAt}ms")
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        screenReceiver = receiver

        // Observation coroutine
        job = CoroutineScope(Dispatchers.Default).launch {
            delay(config.observation_window_s * 1_000L)  // wait full window

            val phoneDownMs: Long = when {
                screenOffTimeMs < 0 -> 0L  // screen never went off
                reUnlockTimeMs > 0  -> reUnlockTimeMs - screenOffTimeMs
                else                -> System.currentTimeMillis() - screenOffTimeMs
            }

            val reUnlockWithin = reUnlockTimeMs > 0 &&
                (reUnlockTimeMs - nudgeFiredAt) < config.re_unlock_window_ms

            val resolved = LabelResolver.resolveAll(
                phoneDownDurationMs      = phoneDownMs,
                notifDismissedInMs       = if (notifDismissMs > 0) notifDismissMs else null,
                reUnlockWithinWindowMs   = reUnlockWithin,
                format                   = format
            )

            Log.d("PresenceAI",
                "PostNudgeObserver resolved: label=${resolved.lrLabel}, " +
                "reward=${resolved.banditReward}, phoneDownMs=$phoneDownMs")

            withContext(Dispatchers.Main) { onResolved(resolved) }
            stop()
        }
    }

    /** Called externally when a notification is dismissed (from NotificationListenerService). */
    fun onNotificationDismissed() {
        if (notifDismissMs < 0) {
            notifDismissMs = System.currentTimeMillis()
            Log.d("PresenceAI", "PostNudgeObserver: notification dismissed")
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        screenReceiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
        }
        screenReceiver = null
    }
}
