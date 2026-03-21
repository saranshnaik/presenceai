package com.nophubbing.presenceai.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nophubbing.presenceai.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * NudgingSystem — delivers HAPTIC or NOTIFICATION nudges.
 *
 * Key contract:
 *  - deliverNudge(format, copy) is the ONLY delivery entry point.
 *  - ViewModel calls this directly after Claude returns copy.
 *  - The notification carries the Claude-generated copy + Accept/Dismiss buttons.
 *  - onScoreUpdate() is NO LONGER used — ViewModel controls delivery timing.
 *  - Cooldown is managed by ViewModel (PostNudgeObserver window).
 */
class NudgingSystem(private val context: Context) {

    companion object {
        private const val TAG        = "NudgingSystem"
        const val CHANNEL_ID         = "presence_nudge_v3"
        const val CHANNEL_NAME       = "Presence Nudge"
        const val NOTIF_ID           = 3003
    }

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _nudgeFired = MutableStateFlow(false)
    val nudgeFired: StateFlow<Boolean> = _nudgeFired.asStateFlow()

    init { createNotificationChannel() }

    fun start() { _isActive.value = true }
    fun stop()  { _isActive.value = false }
    fun destroy() { stop() }

    /**
     * Deliver a nudge with the given Claude-generated copy.
     * Format: HAPTIC = vibration only; NOTIFICATION = system notification with feedback buttons.
     * Called by ViewModel after Claude copy is ready.
     */
    fun deliverNudge(format: com.nophubbing.presenceai.rl.NudgeFormat, copy: String) {
        if (!_isActive.value) return
        _nudgeFired.value = true
        Log.d(TAG, "deliverNudge: format=$format copy=$copy")

        when (format) {
            com.nophubbing.presenceai.rl.NudgeFormat.HAPTIC -> {
                deliverHaptic()
                // Also show a silent notification so user can tap "Why?" and give feedback
                deliverNotification(copy, silent = true)
            }
            com.nophubbing.presenceai.rl.NudgeFormat.NOTIFICATION -> {
                deliverNotification(copy, silent = false)
            }
        }
    }

    /** Legacy entry point kept for compatibility — redirects to deliverNudge with NOTIFICATION. */
    fun onScoreUpdate(score: Float, socialPresent: Boolean) {
        // No-op: ViewModel now calls deliverNudge() directly with Claude copy.
        // Kept so existing code compiles without changes.
    }

    fun acknowledgeNudge() { _nudgeFired.value = false }

    // ── Delivery ──────────────────────────────────────────────────────────────

    private fun deliverHaptic() {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createWaveform(
                    longArrayOf(0, 300, 150, 300, 150, 600), -1
                ))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 300, 150, 300, 150, 600), -1)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Haptic failed: ${e.message}")
        }
    }

    private fun deliverNotification(copy: String, silent: Boolean) {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("from_nudge", true)
        }
        val tapPi = PendingIntent.getActivity(
            context, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val acceptPi = buildActionPi(NudgeFeedbackReceiver.ACTION_ACCEPT, 1)
        val dismissPi = buildActionPi(NudgeFeedbackReceiver.ACTION_DISMISS, 2)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Presence AI")
            .setContentText(copy)
            .setStyle(NotificationCompat.BigTextStyle().bigText(copy))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(tapPi)
            .setAutoCancel(true)
            .addAction(NotificationCompat.Action.Builder(0, "✅ Yes, I was distracted", acceptPi).build())
            .addAction(NotificationCompat.Action.Builder(0, "❌ No, false alarm", dismissPi).build())

        if (silent) {
            builder.setSilent(true)
        } else {
            builder.setDefaults(NotificationCompat.DEFAULT_ALL)
                   .setVibrate(longArrayOf(0, 400, 200, 400))
        }

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID, builder.build())
            Log.d(TAG, "Notification delivered")
        } catch (e: SecurityException) {
            Log.e(TAG, "Notification permission denied: ${e.message}")
        }
    }

    private fun buildActionPi(action: String, reqCode: Int): PendingIntent {
        val intent = Intent(context, NudgeFeedbackReceiver::class.java).apply {
            this.action = action
            putExtra(NudgeFeedbackReceiver.EXTRA_SCORE, 0f)
        }
        return PendingIntent.getBroadcast(
            context, reqCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Gentle presence reminders"
                enableVibration(true)
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
    }
}
