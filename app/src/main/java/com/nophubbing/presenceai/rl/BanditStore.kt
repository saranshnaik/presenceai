package com.nophubbing.presenceai.rl

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * BanditStore.kt — ONLY file that touches SharedPreferences for bandit state.
 * Atomic write: backup key first, then main (crash-safe).
 */
class BanditStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("bandit_state", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_HAPTIC_COUNT   = "haptic_count"
        private const val KEY_HAPTIC_REWARD  = "haptic_reward"
        private const val KEY_NOTIF_COUNT    = "notif_count"
        private const val KEY_NOTIF_REWARD   = "notif_reward"
        // backup keys for crash-safety
        private const val KEY_HAPTIC_COUNT_BK  = "haptic_count_bk"
        private const val KEY_HAPTIC_REWARD_BK = "haptic_reward_bk"
        private const val KEY_NOTIF_COUNT_BK   = "notif_count_bk"
        private const val KEY_NOTIF_REWARD_BK  = "notif_reward_bk"

        /** Static helper to load state without manual instantiation. */
        fun load(context: Context): BanditState = BanditStore(context).load()

        /** Static helper to save state without manual instantiation. */
        fun save(context: Context, state: BanditState) = BanditStore(context).save(state)

        /**
         * Converts a [BanditState] to a JSON string for logging or export.
         */
        fun serialise(state: BanditState): String {
            val json = JSONObject()
            json.put("haptic_count",        state.hapticCount)
            json.put("haptic_total_reward", state.hapticTotalReward.toDouble())
            json.put("notif_count",         state.notifCount)
            json.put("notif_total_reward",  state.notifTotalReward.toDouble())
            return json.toString()
        }

        /**
         * Parses a [BanditState] from a JSON string. Falls back to default state on error.
         */
        fun deserialise(json: String): BanditState {
            return try {
                val obj = JSONObject(json)
                BanditState(
                    hapticCount       = obj.optInt("haptic_count", 0),
                    hapticTotalReward = obj.optDouble("haptic_total_reward", 0.0).toFloat(),
                    notifCount        = obj.optInt("notif_count", 0),
                    notifTotalReward  = obj.optDouble("notif_total_reward", 0.0).toFloat()
                )
            } catch (e: Exception) {
                BanditState()
            }
        }
    }

    fun load(): BanditState {
        return try {
            BanditState(
                hapticCount       = prefs.getInt(KEY_HAPTIC_COUNT, 0),
                hapticTotalReward = prefs.getFloat(KEY_HAPTIC_REWARD, 0f),
                notifCount        = prefs.getInt(KEY_NOTIF_COUNT, 0),
                notifTotalReward  = prefs.getFloat(KEY_NOTIF_REWARD, 0f)
            )
        } catch (e: Exception) {
            // Recover from backup
            BanditState(
                hapticCount       = prefs.getInt(KEY_HAPTIC_COUNT_BK, 0),
                hapticTotalReward = prefs.getFloat(KEY_HAPTIC_REWARD_BK, 0f),
                notifCount        = prefs.getInt(KEY_NOTIF_COUNT_BK, 0),
                notifTotalReward  = prefs.getFloat(KEY_NOTIF_REWARD_BK, 0f)
            )
        }
    }

    fun save(state: BanditState) {
        prefs.edit().apply {
            // Write backup first (crash-safety)
            putInt(KEY_HAPTIC_COUNT_BK,   state.hapticCount)
            putFloat(KEY_HAPTIC_REWARD_BK, state.hapticTotalReward)
            putInt(KEY_NOTIF_COUNT_BK,    state.notifCount)
            putFloat(KEY_NOTIF_REWARD_BK,  state.notifTotalReward)
            apply()
        }
        prefs.edit().apply {
            putInt(KEY_HAPTIC_COUNT,   state.hapticCount)
            putFloat(KEY_HAPTIC_REWARD, state.hapticTotalReward)
            putInt(KEY_NOTIF_COUNT,    state.notifCount)
            putFloat(KEY_NOTIF_REWARD,  state.notifTotalReward)
            apply()
        }
    }
}
