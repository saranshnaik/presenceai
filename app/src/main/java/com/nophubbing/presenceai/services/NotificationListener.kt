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

        Log.d("PresenceAI", "Notification received from ${sbn.packageName}")
    }
}