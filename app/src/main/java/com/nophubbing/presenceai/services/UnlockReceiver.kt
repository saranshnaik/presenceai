package com.nophubbing.presenceai.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

object UnlockCounter {
    var unlockCount = 0
}

class UnlockReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {

        if (intent?.action == Intent.ACTION_USER_PRESENT) {

            UnlockCounter.unlockCount++

            Log.d(
                "PresenceAI",
                "Unlock detected. Count = ${UnlockCounter.unlockCount}"
            )
        }
    }
}