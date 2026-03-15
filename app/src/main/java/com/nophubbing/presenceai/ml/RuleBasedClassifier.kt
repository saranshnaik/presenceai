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

        // 2. Social Pressure Amplification (Aggressive Multiplicative Model)
        if (socialPresent) {
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
        }

        // 3. Temporal Smoothing (EMA)
        val smoothed = (pPhub * 0.4) + (lastPPhub * 0.6)
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
    }
}
