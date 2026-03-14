package com.nophubbing.presenceai.analytics

/**
 * BehaviorSignals.kt — carries ALL data from aggregator to UI/ML.
 */
data class BehaviorSignals(
    val userId: Int = 1,
    val dayNumber: Int = 1,
    val hourOfDay: Int = 0,
    val isEveningSession: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),

    // Baseline values
    val baselineUnlocksPerHour: Float = 3.6f,
    val baselineSessionDurationS: Float = 52.0f,
    val baselineNotifGapS: Float = 21.1f,

    // Live behavioural signals
    val unlockCountPerHour: Float = 0f,
    val microSessionRatio: Float = 0f,
    val notifReflexRatio: Float = 0f,
    val behaviorDriftScore: Float = 0f,
    val timePhaseRisk: Float = 0f,

    // Social context
    val voiceActivityDetected: Int = 0,
    val peopleNearbyCount: Int = 0,
    val vadConfidenceScore: Float = 0f,
    val btSignalStrength: Float = 0f,

    // ML pipeline output
    val pDrift: Float = 0f,
    val presenceScore: Float = 100f,
    val nudgeSent: Int = 0,
    val userResponse: String = "none",
    val isPhubbing: Int = 0,

    // Raw counts for UI
    val unlocks: Int = 0,
    val totalSessions: Int = 0,
    val microSessions: Int = 0,
    val notificationReflexCount: Int = 0,

    // Duration-based fields
    val microSessionDurationS: Float = 0f,
    val notifToUnlockGapS: Float = 0f,

    val categoryBreakdown: List<AppCategoryClassifier.CategoryBreakdown> = emptyList(),
    val dominantCategory: AppCategoryClassifier.Category? = null
)
