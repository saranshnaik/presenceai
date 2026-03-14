package com.nophubbing.presenceai.ml

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Date

object ModelStore {

    /** Creates the export JSON for the current model weights. */
    fun buildAndroidExportJson(weights: LRWeights, config: PipelineConfig): JSONObject {
        val exportData = JSONObject()
        exportData.put("schema_version", "2.0")
        exportData.put("model_type", "logistic_regression")
        exportData.put("weights", JSONArray(weights.asList()))
        exportData.put("bias", weights.bias)
        exportData.put("threshold", config.nudge_threshold)
        exportData.put("feature_names", JSONArray(FEATURE_NAMES))
        exportData.put("update_count", weights.update_count)
        exportData.put("exported_at", Date().toString())
        return exportData
    }

    /** Loads and validates the exported weights JSON. */
    fun loadAndroidExport(jsonString: String): LRWeights {
        val data = JSONObject(jsonString)

        val requiredKeys = listOf(
            "schema_version", "model_type", "weights", "bias",
            "threshold", "feature_names", "update_count", "exported_at"
        )
        for (key in requiredKeys) {
            if (!data.has(key)) {
                throw IllegalArgumentException("Export missing required key: $key")
            }
        }

        val weightsArray = data.getJSONArray("weights")
        if (weightsArray.length() != FEATURE_NAMES.size) {
            throw IllegalArgumentException("Weights length ${weightsArray.length()} != expected ${FEATURE_NAMES.size}")
        }

        val w = (0 until weightsArray.length()).map { weightsArray.getDouble(it) }

        return LRWeights(
            w = w,
            bias = data.getDouble("bias"),
            update_count = data.getInt("update_count"),
            last_updated = data.getString("exported_at")
        )
    }

    fun saveLrWeights(weights: LRWeights, file: File) {
        val json = JSONObject()
        json.put("weights", JSONArray(weights.asList()))
        json.put("bias", weights.bias)
        json.put("update_count", weights.update_count)
        json.put("last_updated", weights.last_updated)

        val tmpPath = File(file.parent, file.name + ".tmp")
        tmpPath.writeText(json.toString(4))
        tmpPath.renameTo(file)
    }

    fun loadLrWeights(file: File): LRWeights {
        if (!file.exists()) {
            return LRWeights.defaults()
        }

        return try {
            val data = JSONObject(file.readText())
            val weightsArray = data.getJSONArray("weights")
            val w = (0 until weightsArray.length()).map { weightsArray.getDouble(it) }
            LRWeights(
                w = w,
                bias = data.optDouble("bias", -2.5),
                update_count = data.optInt("update_count", 0),
                last_updated = data.optString("last_updated", "")
            )
        } catch (e: Exception) {
            LRWeights.defaults()
        }
    }
}
