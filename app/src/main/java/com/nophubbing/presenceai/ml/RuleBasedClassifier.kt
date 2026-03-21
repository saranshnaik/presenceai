package com.nophubbing.presenceai.ml

import android.util.Log

/**
 * RuleBasedClassifier — simplified deterministic phubbing detection.
 *
 * Feature indices:
 *   [0] unlock_freq          normalized [0,4]
 *   [1] micro_session_ratio  [0,1]
 *   [2] notif_reflex         {0,1}
 *   [3] drift_z              [-3,3]
 *   [4] time_phase           {0=day, 1=evening}
 *   [5] vad_energy           {0,1}  voice detected
 *   [6] ble_social           {0,1}  people nearby
 */
object RuleBasedClassifier {

    private const val TAG = "RuleBasedClassifier"

    // EMA state — persists across calls within a session
    @Volatile private var lastPPhub: Double = 0.0
    @Volatile private var nudgeConfirmationSteps: Int = 0

    /**
     * P(drift) — behavioral intensity only.
     * Weights: unlock (0.15) > micro (0.10) > drift (0.10) > reflex (0.05)
     */
    fun computePDrift(features: List<Double>): Double {
        val unlockFreq  = features.getOrElse(0) { 0.0 }
        val microRatio  = features.getOrElse(1) { 0.0 }
        val notifReflex = features.getOrElse(2) { 0.0 }
        val driftZ      = features.getOrElse(3) { 0.0 }

        // Linear scaling for unlock frequency (capped at 4)
        val unlockWeight = (unlockFreq / 4.0).coerceIn(0.0, 1.0) * 0.15

        // Micro-session ratio contribution
        val microWeight = microRatio.coerceIn(0.0, 1.0) * 0.10

        // Drift z-score: linear mapping from [-3, 3] to [0, 1]
        val driftWeight = ((driftZ + 3.0) / 6.0).coerceIn(0.0, 1.0) * 0.10

        // Notification reflex: binary contribution
        val reflexWeight = if (notifReflex > 0.5) 0.05 else 0.0

        // Base constant
        val base = 0.05

        return (base + unlockWeight + microWeight + driftWeight + reflexWeight).coerceIn(0.0, 1.0)
    }

    /**
     * P(phub) — P(drift) adjusted by social context.
     * Weights: voice (0.20) > ble (0.10) > evening (0.05)
     */
    fun computePPhub(features: List<Double>): Double {
        val pDrift       = computePDrift(features)
        val isEvening    = features.getOrElse(4) { 0.0 } > 0.5
        val voiceDetected = features.getOrElse(5) { 0.0 } > 0.5
        val blePresent   = features.getOrElse(6) { 0.0 } > 0.5
        val socialPresent = voiceDetected || blePresent

        var pPhub = pDrift

        if (socialPresent) {
            // Apply boosts based on social context
            val voiceBoost   = if (voiceDetected) 0.20 else 0.0
            val bleBoost     = if (blePresent)    0.10 else 0.0
            val eveningBoost = if (isEvening)     0.05 else 0.0

            pPhub += voiceBoost + bleBoost + eveningBoost
        } else {
            // No social context — attenuate score
            pPhub *= 0.5
        }

        // EMA temporal smoothing (alpha=0.4)
        val smoothed = (pPhub * 0.4) + (lastPPhub * 0.6)
        lastPPhub = smoothed.coerceIn(0.0, 1.0)

        Log.d(TAG, "pDrift=${"%.3f".format(pDrift)} social=$socialPresent " +
            "voice=$voiceDetected ble=$blePresent pPhub=${"%.4f".format(lastPPhub)}")

        return lastPPhub
    }

    /**
     * shouldNudge — requires 3 consecutive steps above threshold
     */
    fun shouldNudge(pPhub: Double, threshold: Double): Boolean {
        if (pPhub >= threshold) {
            nudgeConfirmationSteps++
        } else {
            nudgeConfirmationSteps = 0
        }

        val confirmed = nudgeConfirmationSteps >= 3
        if (confirmed) {
            nudgeConfirmationSteps = 0
            Log.d(TAG, "Nudge CONFIRMED at pPhub=${"%.4f".format(pPhub)}")
        }
        return confirmed
    }

    /** Reset EMA state. */
    fun reset() {
        lastPPhub = 0.0
        nudgeConfirmationSteps = 0
    }
}
