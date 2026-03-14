package com.nophubbing.presenceai.analytics

/**
 * BehaviorSignals.kt — aggregated device signals flowing from MonitoringService → ViewModel.
 * Field names match PRESENCE_AI_56k_FULL.csv column names exactly.
 * Also carries raw counts for the UI (unlocks, totalSessions, etc.).
 */
data class BehaviorSignals(
    // Identity / context
    val userId: Int = 1,
    val dayNumber: Int = 1,
    val hourOfDay: Int,
    val isEveningSession: Int,
    val timestamp: Long = System.currentTimeMillis(),

    // Baseline (personalised reference — starts at dataset mean, adapts over time)
    val baselineUnlocksPerHour: Float = 3.6f,
    val baselineSessionDurationS: Float = 52.0f,
    val baselineNotifGapS: Float = 21.1f,

    // Live behavioural signals
    val unlockCountPerHour: Float,
    val microSessionRatio: Float,         // ratio version for ML
    val notifReflexRatio: Float,          // ratio version for ML
    val behaviorDriftScore: Float,
    val timePhaseRisk: Float,

    // Social context — 0 until permissions granted
    val voiceActivityDetected: Int = 0,   // binary: 1 = voice heard
    val peopleNearbyCount: Int = 0,        // count; >0 = social context confirmed
    val vadConfidenceScore: Float = 0f,
    val btSignalStrength: Float = 0f,

    // ML pipeline output (filled by ViewModel, not service)
    val pDrift: Float = 0f,
    val presenceScore: Float = 100f,
    val nudgeSent: Int = 0,
    val userResponse: String = "none",
    val isPhubbing: Int = 0,

    // Raw UI display counts (populated by SignalAggregator)
    val unlocks: Int = 0,
    val totalSessions: Int = 0,
    val microSessions: Int = 0,
    val notificationReflexCount: Int = 0,

    // Duration-based fields for the 14-feature model
    val microSessionDurationS: Float = 0f,
    val notifToUnlockGapS: Float = 0f,

    // Category breakdown — time spent per app category in this window
    val categoryBreakdown: List<AppCategoryClassifier.CategoryBreakdown> = emptyList(),
    val dominantCategory: AppCategoryClassifier.Category? = null
)
