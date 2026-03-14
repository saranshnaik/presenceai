package com.nophubbing.presenceai.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat

/**
 * NudgeFeedbackReceiver
 *
 * Catches the Accept / Dismiss taps from the nudge notification and
 * hands the raw feedback to FeedbackActivityMonitor for validation + weight update.
 *
 * Register in AndroidManifest.xml:
 *   <receiver android:name=".services.NudgeFeedbackReceiver" android:exported="false"/>
 */
class NudgeFeedbackReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NudgeFeedbackReceiver"

        const val ACTION_ACCEPT  = "com.nophubbing.presenceai.NUDGE_ACCEPT"
        const val ACTION_DISMISS = "com.nophubbing.presenceai.NUDGE_DISMISS"
        const val EXTRA_SCORE    = "nudge_score"
    }

    override fun onReceive(context: Context, intent: Intent) {
        // Dismiss the notification immediately
        NotificationManagerCompat.from(context).cancel(NudgingSystem.NOTIF_ID)

        val score = intent.getFloatExtra(EXTRA_SCORE, -1f)
        val isPhubbing = intent.action == ACTION_ACCEPT

        Log.d(TAG, "Feedback received: isPhubbing=$isPhubbing score=$score")

        // Delegate to the monitor running in the app process.
        // Use a singleton / dependency injection approach in your app.
        FeedbackActivityMonitor.getInstance()?.onUserFeedback(
            userClaimsPhubbing = isPhubbing,
            scoreAtNudge       = score
        )
    }
}
