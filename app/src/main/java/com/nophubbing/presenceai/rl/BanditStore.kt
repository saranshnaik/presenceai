package com.nophubbing.presenceai.rl

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Persistent storage for [BanditState] backed by [SharedPreferences] and serialised as JSON.
 *
 * Design goals:
 * - **Never throws** — every public function is wrapped in a try-catch; failures silently
 *   fallback to a fresh [BanditState] so the bandit never crashes the app.
 * - **No external libraries** — uses only `org.json.JSONObject` and the Android SDK.
 * - **Singleton access** — all calls go through the [BanditStore] object; the underlying
 *   [SharedPreferences] is lazily initialised on the first call that supplies a [Context].
 */
object BanditStore {

    private const val PREFS_NAME    = "presenceai_bandit_store"
    private const val KEY_STATE     = "bandit_state"

    // JSON field names
    private const val F_HAPTIC_COUNT        = "haptic_count"
    private const val F_HAPTIC_TOTAL_REWARD = "haptic_total_reward"
    private const val F_NOTIF_COUNT         = "notif_count"
    private const val F_NOTIF_TOTAL_REWARD  = "notif_total_reward"

    @Volatile
    private var prefs: SharedPreferences? = null

    /** Lazily initialise (or reuse) the SharedPreferences instance. */
    private fun getPrefs(context: Context): SharedPreferences {
        return prefs ?: context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .also { prefs = it }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Loads the persisted [BanditState].
     *
     * Returns a fresh default [BanditState] if no data has been saved yet, or if the
     * stored JSON is malformed.
     *
     * @param context Any [Context]; application context is used internally.
     */
    fun load(context: Context): BanditState {
        return try {
            val json = getPrefs(context).getString(KEY_STATE, null)
                ?: return BanditState()  // nothing saved yet
            deserialise(json)
        } catch (e: Exception) {
            BanditState()  // corrupted data — start fresh
        }
    }

    /**
     * Persists [state] to [SharedPreferences].
     *
     * Silently swallows any exception so a full storage partition or other IO error
     * never propagates to the caller.
     *
     * @param context Any [Context]; application context is used internally.
     * @param state   The [BanditState] to persist.
     */
    fun save(context: Context, state: BanditState) {
        try {
            getPrefs(context).edit()
                .putString(KEY_STATE, serialise(state))
                .apply()
        } catch (e: Exception) {
            // Intentionally silent — persistence failure should not break nudge delivery
        }
    }

    /**
     * Clears all persisted bandit state, resetting both arms to zero.
     *
     * Useful for debugging, onboarding resets, or app reinstalls.
     *
     * @param context Any [Context]; application context is used internally.
     */
    fun reset(context: Context) {
        try {
            getPrefs(context).edit().remove(KEY_STATE).apply()
        } catch (e: Exception) {
            // Intentionally silent
        }
    }

    // ── Serialisation helpers ─────────────────────────────────────────────────

    /**
     * Serialises a [BanditState] to a compact JSON string.
     *
     * Example output:
     * ```json
     * {"haptic_count":7,"haptic_total_reward":3.5,"notif_count":5,"notif_total_reward":1.0}
     * ```
     */
    internal fun serialise(state: BanditState): String =
        JSONObject().apply {
            put(F_HAPTIC_COUNT,        state.hapticCount)
            put(F_HAPTIC_TOTAL_REWARD, state.hapticTotalReward.toDouble())
            put(F_NOTIF_COUNT,         state.notifCount)
            put(F_NOTIF_TOTAL_REWARD,  state.notifTotalReward.toDouble())
        }.toString()

    /**
     * Deserialises a JSON string produced by [serialise] back into a [BanditState].
     *
     * Missing fields default to 0 / 0f so partial JSON (e.g., after a schema change)
     * is handled gracefully.
     *
     * @throws org.json.JSONException if [json] is not valid JSON.
     */
    internal fun deserialise(json: String): BanditState {
        val obj = JSONObject(json)
        return BanditState(
            hapticCount       = obj.optInt(F_HAPTIC_COUNT, 0),
            hapticTotalReward = obj.optDouble(F_HAPTIC_TOTAL_REWARD, 0.0).toFloat(),
            notifCount        = obj.optInt(F_NOTIF_COUNT, 0),
            notifTotalReward  = obj.optDouble(F_NOTIF_TOTAL_REWARD, 0.0).toFloat()
        )
    }
}
