package com.nophubbing.presenceai.ml

import kotlin.math.max
import kotlin.math.min

object FeatureEngineering {

    fun computeX1UnlockFreq(unlockCount10Min: Double, rollingAvg: Double): Double {
        val denom = if (rollingAvg >= 0.1) rollingAvg else 3.0
        val v = unlockCount10Min / denom
        return max(0.0, min(v, 4.0))
    }

    fun computeX2MicroSessionRatio(sessionsUnder30s: Int, totalSessions: Int): Double {
        if (totalSessions == 0) return 0.0
        val v = sessionsUnder30s.toDouble() / totalSessions.toDouble()
        return max(0.0, min(v, 1.0))
    }

    fun computeX3NotificationReflex(lastNotifDeltaMs: Long): Double {
        return if (lastNotifDeltaMs in 1..5000) 1.0 else 0.0
    }

    fun computeX4BehaviorDrift(
        currentUnlockRate: Double,
        baselineMean: Double,
        baselineStdDev: Double,
        baselineReady: Int
    ): Double {
        if (baselineReady == 0 || baselineStdDev < 0.1) return 0.0
        val v = (currentUnlockRate - baselineMean) / baselineStdDev
        return max(-3.0, min(v, 3.0))
    }

    fun computeX5TimePhase(hourOfDay: Int): Double {
        if (hourOfDay !in 0..23) return 0.4
        for ((range, multiplier) in TIME_PHASE_TABLE) {
            if (hourOfDay in range.first..range.second) {
                return multiplier
            }
        }
        return 0.4
    }

    fun computeX6VadEnergy(vadEnergy: Int): Double {
        return if (vadEnergy == 1) 1.0 else 0.0
    }

    fun computeX7BleSocial(bleDeviceCount: Int): Double {
        return if (bleDeviceCount >= 1) 1.0 else 0.0
    }

    fun buildFeatureVector(row: SignalRow): FeatureVector {
        val x1 = computeX1UnlockFreq(row.unlock_count_10min, row.rolling_avg_unlock_rate)
        val x2 = computeX2MicroSessionRatio(row.sessions_under_30s, row.total_sessions)
        val x3 = computeX3NotificationReflex(row.last_notif_delta_ms)
        val x4 = computeX4BehaviorDrift(
            row.current_unlock_rate,
            row.baseline_mean,
            row.baseline_stddev,
            row.baseline_ready
        )
        val x5 = computeX5TimePhase(row.hour_of_day)
        val x6 = computeX6VadEnergy(row.vad_energy)
        val x7 = computeX7BleSocial(row.ble_device_count)

        val features = listOf(x1, x2, x3, x4, x5, x6, x7)
        for (f in features) {
            if (f.isNaN() || f.isInfinite()) {
                throw IllegalArgumentException("Feature is NaN or Inf")
            }
        }

        return FeatureVector(x1, x2, x3, x4, x5, x6, x7)
    }
}
