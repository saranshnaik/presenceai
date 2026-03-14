package com.nophubbing.presenceai.analytics

import android.content.Context
import java.util.Calendar

data class BehaviorSignals(
    val timestamp: Long,
    val x1_unlock_freq: Float,
    val x2_micro_session_ratio: Float,
    val x3_notification_reflex: Float,
    val x4_behavior_drift_z: Float,
    val x5_time_phase: Float,
    val x6_vad_energy: Float,
    val x7_ble_social: Float
)

class SignalAggregator(private val context: Context) {

    fun generateSignals(
        x1: Float,
        x2: Float,
        x3: Float,
        x4: Float,
        x5: Float,
        x6: Float,
        x7: Float
    ): BehaviorSignals {
        return BehaviorSignals(
            System.currentTimeMillis(),
            x1, x2, x3, x4, x5, x6, x7
        )
    }

    private fun computeTimePhase(): Int {

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        return when (hour) {
            in 5..11 -> 0
            in 12..16 -> 1
            in 17..21 -> 2
            else -> 3
        }
    }
}