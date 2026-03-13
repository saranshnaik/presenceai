package com.nophubbing.presenceai.analytics

import android.content.Context
import java.util.Calendar

data class BehaviorSignals(
    val timestamp: Long,
    val unlocks: Int,
    val microSessions: Int,
    val notificationReflex: Int,
    val behaviorDrift: Float,
    val timePhase: Int,
    val voiceDetected: Int,
    val proximityDetected: Int
)

class SignalAggregator(private val context: Context) {

    fun generateSignals(
        unlocks: Int,
        microSessions: Int,
        notificationReflex: Int,
        behaviorDrift: Float,
        voiceDetected: Int,
        micAllowed: Boolean,
        bluetoothAllowed: Boolean
    ): BehaviorSignals {

        val timestamp = System.currentTimeMillis()

        val timePhase = computeTimePhase()

        val voice = if (micAllowed) voiceDetected else -1
        val proximity = if (bluetoothAllowed) 0 else -1

        return BehaviorSignals(
            timestamp,
            unlocks,
            microSessions,
            notificationReflex,
            behaviorDrift,
            timePhase,
            voice,
            proximity
        )
    }

    private fun computeTimePhase(): Int {

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        return when (hour) {
            in 5..11 -> 0
            in 12..16 -> 1
            in 17..21 -> 2
            else -> 3
        }
    }
}