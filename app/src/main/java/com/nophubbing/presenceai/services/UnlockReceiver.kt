package com.nophubbing.presenceai.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

object UnlockCounter {
    var unlockCount = 0
}

class UnlockReceiver : BroadcastReceiver() {

    private var screenOnTime = 0L
    private var screenOffTime = 0L

    override fun onReceive(context: Context?, intent: Intent?) {

        when (intent?.action) {

            Intent.ACTION_SCREEN_OFF -> {

                screenOffTime = System.currentTimeMillis()

                Log.d("PresenceAI", "DETECTED_SCREEN_OFF at $screenOffTime")
            }

            Intent.ACTION_SCREEN_ON -> {

                screenOnTime = System.currentTimeMillis()

                Log.d("PresenceAI", "DETECTED_SCREEN_ON at $screenOnTime")
            }

            Intent.ACTION_USER_PRESENT -> {

                val now = System.currentTimeMillis()

                val timeSinceScreenOn = now - screenOnTime

                /*
                 Unlock pattern:
                 screen on → user present within 15 seconds
                 */

                if (screenOnTime > screenOffTime && timeSinceScreenOn < 15000) {

                    UnlockCounter.unlockCount++

                    Log.d(
                        "PresenceAI",
                        "DETECTED_UNLOCK_BROADCAST via USER_PRESENT. Count=${UnlockCounter.unlockCount}, timeSinceScreenOn=$timeSinceScreenOn"
                    )
                } else {
                    Log.d(
                        "PresenceAI",
                        "USER_PRESENT received but did NOT count as unlock (UNLOCK_BROADCAST_IGNORED). screenOnTime=$screenOnTime, screenOffTime=$screenOffTime, timeSinceScreenOn=$timeSinceScreenOn"
                    )
                }
            }
        }
    }
}