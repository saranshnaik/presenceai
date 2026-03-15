package com.nophubbing.presenceai.ml

import android.util.Log

/**
 * RuleBasedClassifier.kt — A "damn perfect" heuristic model for phubbing detection.
 * Replaces the stochastic ML model with deterministic rules based on social
 * presence, usage intensity, and behavioral drift.
 */
object RuleBasedClassifier {

    private const val TAG = "RuleBasedInference"

    /**
     * computePDrift — Behavioral Drift only (Usage Intensity + Personal Drift)
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
        
        return (pDrift + jitter).coerceIn(0.0, 1.0)
    }

    /**
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
        var pPhub = pDrift

        if (socialPresent) {
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
        }

        // Final deterministic jitter for "realism"
        val microJitter = (Math.sin(pDrift * 1000.0) * 0.005)
        
        val finalScore = (pPhub + microJitter).coerceIn(0.0, 1.0)
        Log.d(TAG, "Rule-Based Inference: pDrift=$pDrift, Social=$socialPresent, pPhub=$finalScore")
        return finalScore
    }

    /**
     * Deterministic decision based on the calculated probability and a hard threshold.
     */
    fun shouldNudge(pPhub: Double, threshold: Double): Boolean {
        // Use a consistent threshold for the heuristic model
        return pPhub >= 0.65
    }
}
