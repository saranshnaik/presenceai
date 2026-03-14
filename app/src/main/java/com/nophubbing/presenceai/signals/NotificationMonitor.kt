package com.nophubbing.presenceai.signals

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Tracks the most recent notification time for calculating notification reflex (x3).
 */
class NotificationMonitor : NotificationListenerService() {
    companion object {
        var lastNotificationTimeMs: Long = 0L
            private set
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        lastNotificationTimeMs = System.currentTimeMillis()
    }

    // Unchanged override - included to prevent the warning from not calling super.
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        // Notification removal doesn't count towards reflex
    }
}
