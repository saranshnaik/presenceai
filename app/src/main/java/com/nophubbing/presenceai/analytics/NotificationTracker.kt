package com.nophubbing.presenceai.analytics

object NotificationTracker {
    @Volatile
    var lastNotificationTime: Long = 0L
}

