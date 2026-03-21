package com.nophubbing.presenceai.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat

/**
 * NudgeFeedbackReceiver — catches Accept / Dismiss taps from the nudge notification.
 *
 * Flow:
 *   User taps button → this BroadcastReceiver fires
 *   → cancels the notification
 *   → calls FeedbackActivityMonitor.onUserFeedback()
 *   → 20s observation window → label derived → OnlineLearner.update() called
 *
 * The bandit reward is handled separately by PostNudgeObserver (45s window).
 * Both run concurrently after a nudge fires — they update different models:
 *   FeedbackActivityMonitor → LR weights (via OnlineLearnerPort)
 *   PostNudgeObserver       → BanditState (via ViewModel.onLabelResolved)
 */
class NudgeFeedbackReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NudgeFeedbackReceiver"
        const val ACTION_ACCEPT  = "com.nophubbing.presenceai.NUDGE_ACCEPT"
        const val ACTION_DISMISS = "com.nophubbing.presenceai.NUDGE_DISMISS"
        const val EXTRA_SCORE    = "nudge_score"
    }

    override fun onReceive(context: Context, intent: Intent) {
        NotificationManagerCompat.from(context).cancel(NudgingSystem.NOTIF_ID)

        val score       = intent.getFloatExtra(EXTRA_SCORE, 0f)
        val isPhubbing  = intent.action == ACTION_ACCEPT

        Log.d(TAG, "Feedback: isPhubbing=$isPhubbing score=$score")

        val monitor = FeedbackActivityMonitor.getInstance()
        if (monitor == null) {
            Log.e(TAG, "FeedbackActivityMonitor not initialized — feedback lost!")
            return
        }
        monitor.onUserFeedback(userClaimsPhubbing = isPhubbing, scoreAtNudge = score)
    }
}
