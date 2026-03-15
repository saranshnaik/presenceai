package com.nophubbing.presenceai.integration

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.nophubbing.presenceai.R
import com.nophubbing.presenceai.rl.NudgeFormat

/**
 * NudgeDelivery — fires VibrationEffect or NotificationCompat.
 */
class NudgeDelivery(private val context: Context) {

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "nudge_channel"

    init {
        createNotificationChannel()
    }

    fun deliver(format: NudgeFormat, copy: String) {
        when (format) {
            NudgeFormat.HAPTIC -> deliverHaptic()
            NudgeFormat.NOTIFICATION -> deliverNotification(copy)
        }
    }

    private fun deliverHaptic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(500)
        }
    }

    private fun deliverNotification(copy: String) {
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Presence AI")
            .setContentText(copy)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1001, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Nudge Notifications"
            val descriptionText = "Gentle reminders to stay present"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
