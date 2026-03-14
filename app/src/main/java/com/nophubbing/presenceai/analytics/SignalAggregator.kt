package com.nophubbing.presenceai.analytics

import android.content.Context
import java.util.Calendar

/**
 * SignalAggregator.kt — maps FeatureExtractor.FeatureMetrics → BehaviorSignals.
 * Produces ALL fields needed by both the 14-feature ML pipeline and the UI.
 */
class SignalAggregator(private val context: Context) {

    fun generateSignals(features: FeatureExtractor.FeatureMetrics): BehaviorSignals {
        val cal     = Calendar.getInstance()
        val hour    = cal.get(Calendar.HOUR_OF_DAY)
        val isEvening = if (hour >= 18 || hour < 5) 1 else 0

        // Baseline values (personalised over time via online learning)
        val baselineUnlocks = 3.6f
        // behavior_drift_score: z-score of unlock rate vs baseline
        val driftScore = (features.unlockCountPerHour - baselineUnlocks) / maxOf(baselineUnlocks, 0.1f)

        // pDrift and presenceScore are filled by DashboardViewModel after ML inference.
        // CSVLogger.updateLastRowLabel() backfills them once the 45s label is resolved.
        return BehaviorSignals(
            hourOfDay               = hour,
            isEveningSession        = isEvening,
            baselineUnlocksPerHour  = baselineUnlocks,
            baselineSessionDurationS = 52.0f,
            baselineNotifGapS       = 21.1f,

            // Live values for ML
            unlockCountPerHour      = features.unlockCountPerHour,
            microSessionRatio       = features.microSessionRatio,
            notifReflexRatio        = features.notifReflexRatio,
            behaviorDriftScore      = driftScore,
            timePhaseRisk           = features.timePhase,
            voiceActivityDetected   = features.vadEnergy.toInt(),
            peopleNearbyCount       = if (features.bleSocial > 0f) 1 else 0,
            vadConfidenceScore      = features.vadEnergy,
            btSignalStrength        = features.bleSocial,

            // Duration-based fields for the 14-feature vector
            microSessionDurationS   = features.avgMicroSessionDurationS,
            notifToUnlockGapS       = features.avgNotifToUnlockGapS,

            // UI display counts
            unlocks                 = features.unlockCountPerHour.toInt(),
            totalSessions           = features.totalSessions,
            microSessions           = features.microSessionCount,
            notificationReflexCount = features.notifReflexCount,

            // Category breakdown
            categoryBreakdown       = features.categoryBreakdown,
            dominantCategory        = features.dominantCategory
        )
    }
}
