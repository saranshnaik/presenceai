package com.nophubbing.presenceai.signals

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * NotificationMonitor — NotificationListenerService for tracking notification reflexes.
 */
class NotificationMonitor : NotificationListenerService() {

    companion object {
        var lastNotificationTimeMs: Long = 0
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        lastNotificationTimeMs = System.currentTimeMillis()
        Log.d("NotificationMonitor", "Notification posted at $lastNotificationTimeMs")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        // This could be used to detect quick dismissals for bandit reward
    }
}
