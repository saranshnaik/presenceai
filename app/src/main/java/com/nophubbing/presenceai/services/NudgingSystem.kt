package com.nophubbing.presenceai.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nophubbing.presenceai.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * NudgingSystem
 *
 * Monitors the phubbing score from the ML pipeline and delivers a gentle
 * notification nudge when the score crosses the configured threshold.
 *
 * Responsibilities:
 *  - Subscribe to incoming phubbing scores
 *  - Fire a notification when score >= threshold AND social presence is confirmed
 *  - Respect a cooldown window to avoid notification spam
 *  - Expose nudge state so the UI / FeedbackActivityMonitor can react
 */
class NudgingSystem(
    private val context    : Context,
    private val cooldownMs : Long = 15 * 1_000L   // 15 seconds for testing
) {

    companion object {
        private const val TAG = "NudgingSystem"

        // Notification channel
        const val CHANNEL_ID   = "presence_nudge_v2"
        const val CHANNEL_NAME = "Presence Nudge"
        const val NOTIF_ID     = 1001

        // Default phubbing score threshold (0–100); anything >= this triggers a nudge
        const val DEFAULT_THRESHOLD = 1f
    }

    // ── Public state ─────────────────────────────────────────────────────────

    /** True while the system is actively monitoring. */
    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    /** Emits true the moment a nudge is delivered. */
    private val _nudgeFired = MutableStateFlow(false)
    val nudgeFired: StateFlow<Boolean> = _nudgeFired.asStateFlow()

    /** Expose the currently configured threshold for display / settings. */
    var threshold: Float = DEFAULT_THRESHOLD

    // ── Private state ─────────────────────────────────────────────────────────

    private var lastNudgeTime = 0L
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    init {
        createNotificationChannel()
    }

    fun start() {
        _isActive.value = true
        Log.d(TAG, "NudgingSystem started. Threshold = $threshold")
    }

    fun stop() {
        _isActive.value = false
        Log.d(TAG, "NudgingSystem stopped.")
    }

    fun destroy() {
        stop()
        scope.cancel()
    }

    // ── Core API ──────────────────────────────────────────────────────────────

    /**
     * Feed the latest phubbing score here (called by PipelineRunner or ViewModel).
     *
     * @param score          0–100 attention presence score
     * @param socialPresent  whether the proximity / voice signals confirm people are nearby
     */
    fun onScoreUpdate(score: Float, socialPresent: Boolean) {
        if (!_isActive.value) return

        val now = System.currentTimeMillis()
        val cooledDown = (now - lastNudgeTime) >= cooldownMs

        Log.d(TAG, "Score=$score threshold=$threshold social=$socialPresent cooledDown=$cooledDown")

        if (score >= threshold && cooledDown) {
            deliverNudge(score)
            lastNudgeTime = now
        }
    }

    /**
     * Called externally (e.g. from FeedbackActivityMonitor) to acknowledge
     * that the user has responded to the nudge — resets the fired flag.
     */
    fun acknowledgeNudge() {
        _nudgeFired.value = false
    }

    // ── Notification helpers ──────────────────────────────────────────────────

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun deliverNudge(score: Float) {
        _nudgeFired.value = true
        Log.d(TAG, "Delivering nudge for score=$score")

        val label   = scoreToLabel(score)
        val message = buildNudgeMessage(score)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("nudge_score", score)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("📱 $label — Is this correct?")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            // Action buttons: user feedback triggers 20s observer → online learning
            .addAction(buildAction("✅ Yes, I was phubbing",  NudgeFeedbackReceiver.ACTION_ACCEPT, score))
            .addAction(buildAction("❌ No, false alarm",       NudgeFeedbackReceiver.ACTION_DISMISS, score))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID, notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "Notification permission not granted: ${e.message}")
        }
    }

    private fun buildAction(label: String, action: String, score: Float): NotificationCompat.Action {
        val intent = Intent(context, NudgeFeedbackReceiver::class.java).apply {
            this.action = action
            putExtra(NudgeFeedbackReceiver.EXTRA_SCORE, score)
        }
        val pi = PendingIntent.getBroadcast(
            context, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(0, label, pi).build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Gentle nudges when phubbing is detected"
                enableVibration(true)
            }
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    // ── Score helpers ─────────────────────────────────────────────────────────

    /** Maps the 0–100 drift score to a human-friendly severity label. */
    private fun scoreToLabel(score: Float): String = when {
        score >= 85 -> "Heavy Drift Detected"
        score >= 70 -> "Moderate Drift"
        else        -> "Light Drift"
    }

    private fun buildNudgeMessage(score: Float): String = when {
        score >= 85 -> "We detected heavy phone usage nearby others. Were you phubbing? Tap to confirm 🙏"
        score >= 70 -> "Looks like you might be distracted. Is this accurate? 😊"
        else        -> "Possible phone distraction detected. Let us know if this is right 👋"
    }
}
