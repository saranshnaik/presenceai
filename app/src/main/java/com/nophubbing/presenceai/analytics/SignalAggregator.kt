package com.nophubbing.presenceai.analytics

import android.content.Context
import java.util.Calendar

data class BehaviorSignals(
    val userId: Int = 1,
    val dayNumber: Int = 1,
    val hourOfDay: Int,
    val isEveningSession: Int,
    val baselineUnlocksPerHour: Float = 3.6f,
    val baselineSessionDurationS: Float = 52.0f,
    val baselineNotifGapS: Float = 21.1f,
    val unlockCountPerHour: Float,
    val microSessionDurationS: Float,
    val notifToUnlockGapS: Float,
    val behaviorDriftScore: Float,
    val timePhaseRisk: Float,
    val voiceActivityDetected: Int = 0,
    val peopleNearbyCount: Int = 0,
    val vadConfidenceScore: Float = 0.0f,
    val btSignalStrength: Float = 0.0f,
    val pDrift: Float,
    val presenceScore: Float,
    val nudgeSent: Int = 0,
    val userResponse: String = "none",
    val isPhubbing: Int = 0,
    
    // UI mapping and ML extraction fallbacks
    val timestamp: Long = System.currentTimeMillis(),
    val totalSessions: Int = 0,
    val unlocks: Int = 0,
    val microSessions: Int = 0,
    val notificationReflex: Int = 0
)

class SignalAggregator(private val context: Context) {

    fun generateSignals(
        features: FeatureExtractor.FeatureMetrics
    ): BehaviorSignals {

        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val isEvening = if (hour >= 18 || hour < 5) 1 else 0
        
        // Simple drift calculation: current unlocks vs baseline (3.6)
        val baselineUnlocks = 3.6f
        val driftScore = (features.unlock_freq - baselineUnlocks) / baselineUnlocks
        
        // Presence score heuristic
        val pDrift = if (driftScore > 0) Math.min(1.0f, driftScore / 2f) else 0.0f
        val presenceScore = Math.max(0.0f, 100.0f * (1.0f - pDrift))

        return BehaviorSignals(
            hourOfDay = hour,
            isEveningSession = isEvening,
            unlockCountPerHour = features.unlock_freq,
            microSessionDurationS = features.micro_session * 10f, // Scale back to representation
            notifToUnlockGapS = features.notification_reflex * 5f, 
            behaviorDriftScore = driftScore,
            timePhaseRisk = features.time_phase,
            pDrift = pDrift,
            presenceScore = presenceScore,
            isPhubbing = if (presenceScore < 40) 1 else 0,
            
            // Hardcoding hardware to 0 as requested
            voiceActivityDetected = 0,
            peopleNearbyCount = 0,
            vadConfidenceScore = 0.0f,
            btSignalStrength = 0.0f,

            // Mapping to UI fields
            unlocks = features.unlock_freq.toInt(),
            totalSessions = features.total_sessions,
            microSessions = features.micro_sessions_count,
            notificationReflex = features.notification_reflex_count
        )
    }
}
