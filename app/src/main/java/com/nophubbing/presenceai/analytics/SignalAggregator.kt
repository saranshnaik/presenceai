package com.nophubbing.presenceai.analytics

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

class SignalAggregator {

    fun generateSignals(
        unlocks: Int,
        microSessions: Int,
        notificationReflex: Int,
        behaviorDrift: Float,
        timePhase: Int,
        voiceDetected: Int,
        proximityDetected: Int,
        micAllowed: Boolean,
        bluetoothAllowed: Boolean
    ): BehaviorSignals {

        val timestamp = System.currentTimeMillis()

        val voice =
            if (micAllowed) voiceDetected else -1

        val proximity =
            if (bluetoothAllowed) proximityDetected else -1

        return BehaviorSignals(
            timestamp = timestamp,
            unlocks = unlocks,
            microSessions = microSessions,
            notificationReflex = notificationReflex,
            behaviorDrift = behaviorDrift,
            timePhase = timePhase,
            voiceDetected = voice,
            proximityDetected = proximity
        )
    }
}