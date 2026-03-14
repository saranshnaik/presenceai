package com.nophubbing.presenceai.ml

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Date

/**
 * ModelStore — ONLY class that reads/writes LR weights to disk.
 * Uses atomic tmp→rename pattern for crash safety.
 *
 * Storage: context.filesDir/lr_weights.json
 * Guard: only seeds from defaults when update_count == 0 (never overwrites learned weights).
 */
object ModelStore {

    private const val WEIGHTS_FILE  = "lr_weights.json"
    private const val SCHEMA_VERSION = "2.0"

    fun loadWeights(context: Context): LRWeights {
        val file = File(context.filesDir, WEIGHTS_FILE)
        if (!file.exists()) {
            Log.d("PresenceAI", "ModelStore: no saved weights, using defaults")
            return LRWeights.defaults()
        }
        return try {
            val data = JSONObject(file.readText())
            val wArray = data.getJSONArray("weights")
            val w = (0 until wArray.length()).map { wArray.getDouble(it) }
            if (w.size != FEATURE_NAMES.size) {
                Log.w("PresenceAI", "ModelStore: weight count mismatch, using defaults")
                return LRWeights.defaults()
            }
            LRWeights(
                w            = w,
                bias         = data.optDouble("bias", -2.5),
                update_count = data.optInt("update_count", 0),
                last_updated = data.optString("last_updated", "")
            ).also { Log.d("PresenceAI", "ModelStore: loaded weights (${it.update_count} updates)") }
        } catch (e: Exception) {
            Log.e("PresenceAI", "ModelStore load failed, using defaults: ${e.message}")
            LRWeights.defaults()
        }
    }

    fun saveWeights(context: Context, weights: LRWeights) {
        try {
            val json = JSONObject().apply {
                put("schema_version", SCHEMA_VERSION)
                put("model_type", "logistic_regression_14f")
                put("weights", JSONArray(weights.asList()))
                put("bias", weights.bias)
                put("update_count", weights.update_count)
                put("last_updated", weights.last_updated)
                put("feature_names", JSONArray(FEATURE_NAMES))
            }
            // Atomic write: tmp file then rename
            val dir  = context.filesDir
            val tmp  = File(dir, "$WEIGHTS_FILE.tmp")
            val dest = File(dir, WEIGHTS_FILE)
            tmp.writeText(json.toString(2))
            tmp.renameTo(dest)
            Log.d("PresenceAI", "ModelStore: saved weights (${weights.update_count} updates)")
        } catch (e: Exception) {
            Log.e("PresenceAI", "ModelStore save failed: ${e.message}")
        }
    }

    /** Seed from defaults only if weights have never been updated (cold start guard). */
    fun seedIfEmpty(context: Context): LRWeights {
        val existing = loadWeights(context)
        return if (existing.update_count == 0) {
            Log.d("PresenceAI", "ModelStore: cold start — using default weights")
            LRWeights.defaults()
        } else {
            existing
        }
    }

    fun buildExportJson(weights: LRWeights): JSONObject = JSONObject().apply {
        put("schema_version", SCHEMA_VERSION)
        put("model_type", "logistic_regression_14f")
        put("weights", JSONArray(weights.asList()))
        put("bias", weights.bias)
        put("threshold", 0.65)
        put("feature_names", JSONArray(FEATURE_NAMES))
        put("update_count", weights.update_count)
        put("exported_at", Date().toString())
    }
}
