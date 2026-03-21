package com.nophubbing.presenceai.integration

import android.util.Log
import com.nophubbing.presenceai.genai.ContextBuilder
import com.nophubbing.presenceai.genai.NudgeCopyGenerator
import com.nophubbing.presenceai.ml.FeatureEngineering
import com.nophubbing.presenceai.ml.LrClassifier
import com.nophubbing.presenceai.ml.LRWeights
import com.nophubbing.presenceai.ml.PipelineConfig
import com.nophubbing.presenceai.ml.SignalRow
import com.nophubbing.presenceai.rl.Bandit
import com.nophubbing.presenceai.rl.BanditState
import com.nophubbing.presenceai.rl.NudgeFormat
import com.nophubbing.presenceai.analytics.BehaviorSignals

/**
 * InferenceCycle — features → P(phub) → bandit → copy.
 * Chain: ML → RL → GenAI. Never run in parallel.
 */
object InferenceCycle {

    data class Result(
        val pDrift: Float,
        val pPhub: Float,
        val format: NudgeFormat?,
        val copy: String?
    )

    suspend fun run(
        signals: BehaviorSignals,
        row: SignalRow,
        weights: LRWeights,
        banditState: BanditState
    ): Result {
        val features = FeatureEngineering.buildFeatureVector(row)
        val fvList = features.map { it.toDouble() }

        // PipelineConfig is an object — pass it directly
        val pDrift = LrClassifier.predict(fvList, weights).toFloat()
        val pPhub  = LrClassifier.computePPhub(fvList, weights, PipelineConfig).toFloat()
        val shouldNudge = LrClassifier.shouldNudge(fvList, weights, PipelineConfig)

        if (!shouldNudge) return Result(pDrift, pPhub, null, null)

        // RL fires only when ML says nudge (Coupling Point 2)
        val format = Bandit.selectAction(banditState)

        // GenAI — ContextBuilder bridges analytics → genai without cross-layer imports
        val ctx = ContextBuilder.buildNudgeContext(signals, pPhub)
        val copy = NudgeCopyGenerator.generateNudgeCopy(ctx)

        Log.d("PresenceAI", "InferenceCycle: pPhub=$pPhub format=$format")
        return Result(pDrift, pPhub, format, copy)
    }
}
