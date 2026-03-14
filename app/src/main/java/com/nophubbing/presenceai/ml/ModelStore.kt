package com.nophubbing.presenceai.ml

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Date

object ModelStore {

    /**
     * Creates the explicit Plan B handoff file for Android ingestion format.
     */
    fun buildAndroidExportJson(weights: LRWeights, config: PipelineConfig): JSONObject {
        val exportData = JSONObject()
        exportData.put("schema_version", "1.0")
        exportData.put("model_type", "logistic_regression")
        
        val wArray = JSONArray(weights.asList())
        exportData.put("weights", wArray)
        exportData.put("bias", weights.bias)
        exportData.put("threshold", config.nudge_threshold)
        
        val fNamesArray = JSONArray(FEATURE_NAMES)
        exportData.put("feature_names", fNamesArray)
        
        exportData.put("update_count", weights.update_count)
        exportData.put("exported_at", Date().toString())
        
        return exportData
    }

    /**
     * Loads and strictly validates the Android export schema from a raw JSON string.
     */
    fun loadAndroidExport(jsonString: String): LRWeights {
        val data = JSONObject(jsonString)
        
        val requiredKeys = listOf(
            "schema_version", "model_type", "weights", "bias",
            "threshold", "feature_names", "update_count", "exported_at"
        )

        for (key in requiredKeys) {
            if (!data.has(key)) {
                throw IllegalArgumentException("Android export missing required key: $key")
            }
        }

        val weightsArray = data.getJSONArray("weights")
        if (weightsArray.length() != 7) {
            throw IllegalArgumentException("Weights array length must be exactly 7")
        }

        return LRWeights(
            w1 = weightsArray.getDouble(0),
            w2 = weightsArray.getDouble(1),
            w3 = weightsArray.getDouble(2),
            w4 = weightsArray.getDouble(3),
            w5 = weightsArray.getDouble(4),
            w6 = weightsArray.getDouble(5),
            w7 = weightsArray.getDouble(6),
            bias = data.getDouble("bias"),
            update_count = data.getInt("update_count"),
            last_updated = data.getString("exported_at")
        )
    }

    fun saveLrWeights(weights: LRWeights, file: File) {
        val json = JSONObject()
        json.put("w1", weights.w1)
        json.put("w2", weights.w2)
        json.put("w3", weights.w3)
        json.put("w4", weights.w4)
        json.put("w5", weights.w5)
        json.put("w6", weights.w6)
        json.put("w7", weights.w7)
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
            LRWeights(
                w1 = if (data.has("w1")) data.getDouble("w1") else DEFAULT_WEIGHTS["w1"]!!,
                w2 = if (data.has("w2")) data.getDouble("w2") else DEFAULT_WEIGHTS["w2"]!!,
                w3 = if (data.has("w3")) data.getDouble("w3") else DEFAULT_WEIGHTS["w3"]!!,
                w4 = if (data.has("w4")) data.getDouble("w4") else DEFAULT_WEIGHTS["w4"]!!,
                w5 = if (data.has("w5")) data.getDouble("w5") else DEFAULT_WEIGHTS["w5"]!!,
                w6 = if (data.has("w6")) data.getDouble("w6") else DEFAULT_WEIGHTS["w6"]!!,
                w7 = if (data.has("w7")) data.getDouble("w7") else DEFAULT_WEIGHTS["w7"]!!,
                bias = if (data.has("bias")) data.getDouble("bias") else DEFAULT_WEIGHTS["bias"]!!,
                update_count = if (data.has("update_count")) data.getInt("update_count") else 0,
                last_updated = if (data.has("last_updated")) data.getString("last_updated") else ""
            )
        } catch (e: Exception) {
            LRWeights.defaults()
        }
    }
}
