package com.nophubbing.presenceai.ml

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.sqrt

/**
 * BaselineTracker — EWMA mean/variance tracker for behavior drift (x4).
 * Persists to SharedPreferences.
 */
class BaselineTracker(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("baseline_stats", Context.MODE_PRIVATE)

    fun updateBaseline(currentRate: Float) {
        val alpha = PipelineConfig.EWMA_ALPHA
        val count = getSampleCount()
        val oldMean = getMean()
        val oldVar = getVariance()

        val newMean = (1 - alpha) * oldMean + alpha * currentRate
        
        // EWMA Variance formula: Var_t = (1-alpha)*Var_{t-1} + alpha*(rate - mean_old)*(rate - mean_new)
        val newVar = (1 - alpha) * oldVar + alpha * (currentRate - oldMean) * (currentRate - newMean)
        
        prefs.edit()
            .putFloat("mean", newMean)
            .putFloat("variance", newVar)
            .putInt("sample_count", count + 1)
            .apply()
    }

    fun getMean(): Float = prefs.getFloat("mean", 0f)
    fun getVariance(): Float = prefs.getFloat("variance", 0f)
    fun getStdDev(): Float = sqrt(getVariance())
    fun getSampleCount(): Int = prefs.getInt("sample_count", 0)
    
    fun isReady(): Boolean = getSampleCount() >= PipelineConfig.MIN_SAMPLES_FOR_BASELINE
}
