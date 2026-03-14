package com.nophubbing.presenceai.services

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.nophubbing.presenceai.analytics.NotificationTracker

object NotificationCounter {
    var notificationCount = 0
}

class NotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {

        NotificationCounter.notificationCount++
        NotificationTracker.lastNotificationTime = System.currentTimeMillis()

        // #region agent log
        try {
            val logEntry = """
{"sessionId":"86e374","runId":"pre-fix","hypothesisId":"H1","location":"NotificationListener.kt:15","message":"notification_posted","data":{"packageName":"${sbn.packageName}","notificationCount":${NotificationCounter.notificationCount},"lastNotificationTime":${NotificationTracker.lastNotificationTime}},"timestamp":${System.currentTimeMillis()}}
""".trimIndent()
            java.io.File("debug-86e374.log").appendText(logEntry + "\n")
        } catch (_: Exception) {
        }
        // #endregion

        Log.d(
            "PresenceAI",
            "Notification received from ${sbn.packageName}"
        )
    }
}