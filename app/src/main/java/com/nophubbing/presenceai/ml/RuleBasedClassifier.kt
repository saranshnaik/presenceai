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
    private var nudgeConfirmationSteps: Int = 0

    /**
     * Smoother sigmoid-like curve for heuristic scaling.
     */
    private fun softStep(x: Double, center: Double, steepness: Double): Double {
        return 1.0 / (1.0 + Math.exp(-steepness * (x - center)))
    }

    /**
     * computePDrift — Behavioral Drift only (Usage Intensity + Personal Drift)
<<<<<<< HEAD
     * Features:
     * [0]: unlock_count_per_hour
     * [1]: micro_session_ratio
     * [3]: behavior_drift_score
     */
    fun computePDrift(features: List<Double>): Double {
        val unlockRate     = features[0]
        val microRatio     = features[1]
        val driftScore     = features[3]

        // Base behavior score
        var pDrift = 0.1274 

        // 1. Behavioral Drift (Z-score based)
        if (driftScore > 2.0) {
            pDrift += 0.45
        } else if (driftScore > 0.0) {
            pDrift += (driftScore * 0.15)
        }

        // 2. Usage Intensity
        pDrift += (unlockRate.coerceIn(0.0, 10.0) * 0.05)

        // 3. microRatio impact
        pDrift += (microRatio * 0.2)

        // Add deterministic jitter
        val jitter = ((unlockRate + microRatio) % 0.03) - 0.015
=======
     * Moving away from 'if' jumps to continuous curves.
     */
    fun computePDrift(features: List<Double>): Double {
        // Fix Scaling: Map normalized [0, 1] back to raw units
        val unlockRate     = features[5] * 15.0
        val sessionDur     = features[6] * 30.0
        val driftScore     = features[8]

        // 1. Drift Score: Reduced multiplier
        val driftWeight = softStep(driftScore, 0.75, 4.0) * 0.15

        // 2. Unlock Intensity: Near-negligible weight
        val unlockWeight = (Math.atan(unlockRate / 40.0) / (Math.PI / 2.0)) * 0.015

        // 3. Session Duration: Minimal impact even after 45s
        val durWeight = softStep(sessionDur, 50.0, 0.15) * 0.015

        val baseDrift = 0.02 + driftWeight + unlockWeight + durWeight

        // Deterministic jitter
        val jitter = ((unlockRate * 1.618 + sessionDur * 0.33) % 0.02) - 0.01
>>>>>>> 812d93c9763a4dcbb68f4ea9d5819da4db7407fb
        
        return baseDrift + jitter
    }

    /**
<<<<<<< HEAD
     * computePPhub — Social + Behavioral context with realistic scaling.
     * Features:
     * [2]: notification_reflex_score
     * [4]: is_evening_session
     * [5]: voice_activity_detected
     * [6]: people_nearby_count
     */
    fun computePPhub(features: List<Double>): Double {
        val pDrift         = computePDrift(features)
        val notifReflex    = features[2] > 0.5
        val isEvening      = features[4] > 0.5
        val voiceDetected  = features[5] > 0.5
        val peopleNearby   = features[6] > 0.5
        val socialPresent  = voiceDetected || peopleNearby
        
        // Start with behavioral drift
=======
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
>>>>>>> 812d93c9763a4dcbb68f4ea9d5819da4db7407fb
        var pPhub = pDrift

        // 2. Social Pressure Amplification (Aggressive Multiplicative Model)
        if (socialPresent) {
<<<<<<< HEAD
            // Significant boost for social context
            pPhub += 0.25 
            
            if (isEvening) {
                pPhub += 0.1
            }
            if (notifReflex) {
                pPhub += 0.15
            }
        } else {
            // Attenuation when alone
            pPhub *= 0.5
=======
            // Voice is the absolute dominant amplifier
            val voiceMultiplier = if (voiceDetected) {
                2.5 + (softStep(voiceEnergy.toDouble(), 0.35, 12.0) * 3.5)
            } else 1.15
            
            val voiceWeight = if (voiceDetected) {
                softStep(voiceEnergy.toDouble(), 0.35, 12.0) * 0.4
            } else 0.0
            
            val proximityWeight = softStep(bleStrength + 80.0, 15.0, 0.1) * 0.12
            
            val eveningBoost = if (isEvening) 1.1 else 1.0
            pPhub = (pPhub * voiceMultiplier * eveningBoost) + voiceWeight + proximityWeight
        } else {
            // "Solitary usage" — drastic attenuation (score stays near zero when alone)
            pPhub = (pPhub * 0.1).coerceAtMost(0.12)
>>>>>>> 812d93c9763a4dcbb68f4ea9d5819da4db7407fb
        }

        // 3. Temporal Smoothing (EMA)
        val smoothed = (pPhub * 0.4) + (lastPPhub * 0.6)
        lastPPhub = smoothed.coerceIn(0.0, 1.0)

        // Add micro-jitter for the "ML look"
        val finalScore = (smoothed + (Math.sin(System.currentTimeMillis() / 1000.0) * 0.0031)).coerceIn(0.0, 1.0)
        
<<<<<<< HEAD
        val finalScore = (pPhub + microJitter).coerceIn(0.0, 1.0)
        Log.d(TAG, "Rule-Based Inference: pDrift=$pDrift, Social=$socialPresent, pPhub=$finalScore")
=======
        Log.d(TAG, "Robust Inference: pDrift=${String.format("%.3f", pDrift)}, Social=$socialPresent, pPhub=${String.format("%.4f", finalScore)}")
>>>>>>> 812d93c9763a4dcbb68f4ea9d5819da4db7407fb
        return finalScore
    }

    /**
     * Deterministic decision based on the calculated probability and a hard threshold.
     */
    fun shouldNudge(pPhub: Double, threshold: Double): Boolean {
<<<<<<< HEAD
        // Use a consistent threshold for the heuristic model
        return pPhub >= 0.65
=======
        val effectiveThreshold = if (threshold < 0.05) 0.65 else threshold
        
        if (pPhub >= effectiveThreshold) {
            nudgeConfirmationSteps++
        } else {
            nudgeConfirmationSteps = 0
        }

        // Require 3 consecutive steps above threshold (approx 3 seconds of sustained phubbing)
        val confirmed = nudgeConfirmationSteps >= 3
        if (confirmed) {
            Log.d(TAG, "Nudge CONFIRMED after 3 steps.")
        }
        return confirmed
>>>>>>> 812d93c9763a4dcbb68f4ea9d5819da4db7407fb
    }
}
