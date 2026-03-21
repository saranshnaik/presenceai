package com.nophubbing.presenceai.genai

import com.nophubbing.presenceai.analytics.BehaviorSignals

/**
 * ContextBuilder — converts ML signal objects into genai context DTOs.
 *
 * Architecture rule: genai/ must not import from ml/ or rl/.
 * DashboardViewModel calls ContextBuilder with BehaviorSignals + plain values,
 * which are already in the analytics layer (no ml/ import needed here).
 */
object ContextBuilder {

    fun buildNudgeContext(
        signals: BehaviorSignals,
        pPhub: Float
    ): NudgeContext = NudgeContext(
        unlockCount  = signals.unlocks,
        microSessions = signals.microSessions,
        notifReflex  = signals.notificationReflexCount > 0,
        pPhub        = pPhub,
        voicePresent = signals.voiceActivityDetected > 0,
        blePresent   = signals.peopleNearbyCount > 0,
        hourOfDay    = signals.hourOfDay
    )

    fun buildNudgeEvent(
        signals: BehaviorSignals,
        pPhub: Float,
        format: String,
        behaviorDrift: Float
    ): NudgeEvent = NudgeEvent(
        unlockCount   = signals.unlocks,
        microSessions = signals.microSessions,
        notifReflex   = signals.notificationReflexCount > 0,
        pPhub         = pPhub,
        format        = format,
        voicePresent  = signals.voiceActivityDetected > 0,
        blePresent    = signals.peopleNearbyCount > 0,
        hourOfDay     = signals.hourOfDay,
        behaviorDrift = behaviorDrift
    )
}
