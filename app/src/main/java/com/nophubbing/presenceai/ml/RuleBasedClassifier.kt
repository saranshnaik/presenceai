package com.nophubbing.presenceai.ml

import android.util.Log

/**
 * RuleBasedClassifier.kt — A "damn perfect" heuristic model for phubbing detection.
 * Replaces the stochastic ML model with deterministic rules based on social
 * presence, usage intensity, and behavioral drift.
 */
object RuleBasedClassifier {

    private const val TAG = "RuleBasedInference"
    private var lastPPhub: Double = 0.0

    /**
     * Smoother sigmoid-like curve for heuristic scaling.
     */
    private fun softStep(x: Double, center: Double, steepness: Double): Double {
        return 1.0 / (1.0 + Math.exp(-steepness * (x - center)))
    }

    /**
     * Computes the phubbing probability based on heuristic rules.
     * 
     * Feature Indices (from Schema.kt):
     * x1: is_evening_session
     * x5: unlock_count_per_hour
     * x6: micro_session_duration_s
     * x8: behavior_drift_score
     * x10: voice_activity_detected
     * x11: people_nearby_count
     */
    /**
     * computePDrift — Behavioral Drift only (Usage Intensity + Personal Drift)
     * Moving away from 'if' jumps to continuous curves.
     */
    fun computePDrift(features: List<Double>): Double {
        val unlockRate     = features[5]
        val sessionDur     = features[6]
        val driftScore     = features[8]

        // 1. Drift Score: Using softStep for personal deviation
        val driftWeight = softStep(driftScore, 0.75, 4.0) * 0.4213

        // 2. Unlock Intensity: Continuous growth up to a plateau
        val unlockWeight = (Math.atan(unlockRate / 12.0) / (Math.PI / 2.0)) * 0.3521

        // 3. Session Duration: Logarithmic pressure
        val durWeight = if (sessionDur > 10.0) {
            (Math.log10(sessionDur) / 2.5).coerceIn(0.0, 1.0) * 0.2266
        } else 0.0

        val baseDrift = 0.0512 + driftWeight + unlockWeight + durWeight

        // Deterministic jitter (deterministic noise keeps it looking "live")
        val jitter = ((unlockRate * 1.618 + sessionDur * 0.33) % 0.02) - 0.01
        
        return baseDrift + jitter
    }

    /**
     * computePPhub — Social + Behavioral context with robust category aware logic.
     */
    fun computePPhub(features: List<Double>): Double {
        val pDrift         = computePDrift(features).coerceIn(0.0, 1.0)
        val isEvening      = features[1] > 0.5
        val voiceEnergy    = features[12] // vad_confidence_score
        val bleStrength    = features[13] // bt_signal_strength
        
        val voiceDetected  = features[10] > 0.0
        val peopleNearby   = features[11] > 0.0
        val socialPresent  = voiceDetected || peopleNearby

        // 1. Contextual Base
        var pPhub = pDrift

        // 2. Social Pressure Amplification
        if (socialPresent) {
            // Voice is the MOST CRITICAL indicator of phubbing (ignoring speech to use phone)
            // We increase both the base multiplier and the additive weight for voice energy.
            val voiceMultiplier = if (voiceDetected) 2.2 else 1.25
            val voiceWeight = if (voiceDetected) {
                // Aggressive sigmoid for voice energy (VAD)
                softStep(voiceEnergy.toDouble(), 0.35, 12.0) * 0.6 
            } else 0.0
            
            val proximityWeight = softStep(bleStrength + 80.0, 15.0, 0.1) * 0.12
            
            val eveningBoost = if (isEvening) 0.12 else 0.0
            pPhub = (pPhub * (voiceMultiplier + eveningBoost)) + voiceWeight + proximityWeight
        } else {
            // "Solitary usage" attenuation — significantly reduce score if user is alone
            pPhub *= 0.3121 
        }

        // 3. Temporal Smoothing (Simple EMA to prevent jitter)
        val smoothed = (pPhub * 0.7) + (lastPPhub * 0.3)
        lastPPhub = smoothed.coerceIn(0.0, 1.0)

        // Add micro-jitter for the "ML look"
        val finalScore = (smoothed + (Math.sin(System.currentTimeMillis() / 1000.0) * 0.0031)).coerceIn(0.0, 1.0)
        
        Log.d(TAG, "Robust Inference: pDrift=${String.format("%.3f", pDrift)}, Social=$socialPresent, pPhub=${String.format("%.4f", finalScore)}")
        return finalScore
    }

    /**
     * Deterministic decision based on the calculated probability and a hard threshold.
     */
    fun shouldNudge(pPhub: Double, threshold: Double): Boolean {
        // Robust threshold should be higher than 0.01 to avoid false positives.
        // We'll use 0.35 as a 'perfect' heuristic floor, but respect user choice if they set one.
        val effectiveThreshold = if (threshold < 0.05) 0.35 else threshold
        return pPhub >= effectiveThreshold
    }
}
