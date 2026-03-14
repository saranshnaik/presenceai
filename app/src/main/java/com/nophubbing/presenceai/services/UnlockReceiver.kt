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

                Log.d("PresenceAI", "Screen turned off")
            }

            Intent.ACTION_SCREEN_ON -> {

                screenOnTime = System.currentTimeMillis()

                Log.d("PresenceAI", "Screen turned on")
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
                        "Unlock detected. Count=${UnlockCounter.unlockCount}"
                    )
                }
            }
        }
    }
}