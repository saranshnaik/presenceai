package com.nophubbing.presenceai.integration

import android.content.Context
import android.content.SharedPreferences
import com.nophubbing.presenceai.ml.PipelineConfig

/**
 * NudgeGate — kill switch, daily limit, suppress rules.
 */
class NudgeGate(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("nudge_gate", Context.MODE_PRIVATE)

    fun canNudge(): Boolean {
        if (isKillSwitchActive()) return false
        if (getDailyNudgeCount() >= PipelineConfig.MAX_NUDGES_PER_DAY) return false
        return true
    }

    fun recordNudge() {
        val today = System.currentTimeMillis() / (24 * 60 * 60 * 1000)
        val count = getDailyNudgeCount()
        prefs.edit()
            .putInt("nudge_count_$today", count + 1)
            .apply()
    }

    fun activateKillSwitch() {
        prefs.edit()
            .putLong("kill_switch_until", System.currentTimeMillis() + PipelineConfig.KILL_SWITCH_DURATION_MS)
            .apply()
    }

    private fun isKillSwitchActive(): Boolean {
        val until = prefs.getLong("kill_switch_until", 0)
        return System.currentTimeMillis() < until
    }

    private fun getDailyNudgeCount(): Int {
        val today = System.currentTimeMillis() / (24 * 60 * 60 * 1000)
        return prefs.getInt("nudge_count_$today", 0)
    }
}
