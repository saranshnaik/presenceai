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
 * Schema versioning:
 *   schema_version "1.x" or missing → old miscalibrated weights (bias ~-2.5) → force reset
 *   schema_version "2.0"            → recalibrated weights (bias -1.20)       → load normally
 */
object ModelStore {

    private const val WEIGHTS_FILE   = "lr_weights.json"
    private const val SCHEMA_VERSION = "2.0"

    fun loadWeights(context: Context): LRWeights {
        val file = File(context.filesDir, WEIGHTS_FILE)
        if (!file.exists()) {
            Log.d("PresenceAI", "ModelStore: no saved weights file")
            return LRWeights.defaults()
        }
        return try {
            val data    = JSONObject(file.readText())
            val version = data.optString("schema_version", "1.0")

            // Any schema older than 2.0 → weights are miscalibrated, delete and reset
            if (!version.startsWith("2")) {
                Log.w("PresenceAI", "ModelStore: old schema v$version detected — deleting and resetting to v2 defaults")
                file.delete()
                return LRWeights.defaults()
            }

            val wArray = data.getJSONArray("weights")
            val w = (0 until wArray.length()).map { wArray.getDouble(it) }
            if (w.size != FEATURE_NAMES.size) {
                Log.w("PresenceAI", "ModelStore: weight count mismatch (${w.size} vs ${FEATURE_NAMES.size}), resetting")
                file.delete()
                return LRWeights.defaults()
            }

            val bias = data.optDouble("bias", -1.20)

            // Safety net: if bias is still the old miscalibrated value, reset
            if (bias <= -2.0) {
                Log.w("PresenceAI", "ModelStore: miscalibrated bias ($bias) in v2 file — resetting")
                file.delete()
                return LRWeights.defaults()
            }

            LRWeights(
                w            = w,
                bias         = bias,
                update_count = data.optInt("update_count", 0),
                last_updated = data.optString("last_updated", "")
            ).also {
                Log.d("PresenceAI", "ModelStore: loaded v2 weights (${it.update_count} updates, bias=${it.bias})")
            }
        } catch (e: Exception) {
            Log.e("PresenceAI", "ModelStore load failed, resetting: ${e.message}")
            try { File(context.filesDir, WEIGHTS_FILE).delete() } catch (_: Exception) {}
            LRWeights.defaults()
        }
    }

    fun saveWeights(context: Context, weights: LRWeights) {
        try {
            val json = JSONObject().apply {
                put("schema_version", SCHEMA_VERSION)
                put("model_type",     "logistic_regression_14f_v2")
                put("weights",        JSONArray(weights.asList()))
                put("bias",           weights.bias)
                put("update_count",   weights.update_count)
                put("last_updated",   weights.last_updated)
                put("feature_names",  JSONArray(FEATURE_NAMES))
            }
            val tmp  = File(context.filesDir, "$WEIGHTS_FILE.tmp")
            val dest = File(context.filesDir, WEIGHTS_FILE)
            tmp.writeText(json.toString(2))
            tmp.renameTo(dest)
            Log.d("PresenceAI", "ModelStore: saved weights (${weights.update_count} updates, bias=${weights.bias})")
        } catch (e: Exception) {
            Log.e("PresenceAI", "ModelStore save failed: ${e.message}")
        }
    }

    /**
     * Returns the weights to use on startup.
     * Always loads fresh — schema version check inside loadWeights() handles reset.
     * The caller should use the returned weights unconditionally (no update_count guard).
     */
    fun loadForStartup(context: Context): LRWeights = loadWeights(context)

    fun buildExportJson(weights: LRWeights): JSONObject = JSONObject().apply {
        put("schema_version", SCHEMA_VERSION)
        put("model_type",     "logistic_regression_14f_v2")
        put("weights",        JSONArray(weights.asList()))
        put("bias",           weights.bias)
        put("threshold",      0.65)
        put("feature_names",  JSONArray(FEATURE_NAMES))
        put("update_count",   weights.update_count)
        put("exported_at",    Date().toString())
    }
}
