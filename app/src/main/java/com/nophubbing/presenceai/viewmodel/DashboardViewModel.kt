package com.nophubbing.presenceai.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nophubbing.presenceai.analytics.AppCategoryClassifier
import com.nophubbing.presenceai.integration.PostNudgeObserver
import com.nophubbing.presenceai.storage.CSVLogger
import com.nophubbing.presenceai.analytics.BehaviorSignals
import com.nophubbing.presenceai.analytics.SignalRepository
import com.nophubbing.presenceai.ml.*
import com.nophubbing.presenceai.rl.Bandit
import com.nophubbing.presenceai.rl.BanditState
import com.nophubbing.presenceai.rl.BanditStore
import com.nophubbing.presenceai.rl.NudgeFormat
import com.nophubbing.presenceai.storage.CSVReader
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * DashboardViewModel
 *
 * TWO update paths — both write to the same StateFlows:
 *
 * 1. FAST PATH (every 5s via MonitoringService heartbeat):
 *    MonitoringService → SignalRepository → collectLatest → processSignals()
 *    Full 14-feature pipeline. Accurate. Used for ML inference + nudge decisions.
 *
 * 2. LIVE TICKER (every 1s, internal):
 *    Reads latest BehaviorSignals already in _signals.
 *    Re-runs inference with a "time elapsed" boost on unlock rate so the
 *    score visibly moves every second without needing a new UsageStats query.
 *    This is display-only — weights are NOT updated by the ticker.
 *
 * Result: the presence circle animates smoothly every second.
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val app    = application
    private val config = PipelineConfig()

    private val pipelineRunner = PipelineRunner(config).also { runner ->
        val saved = ModelStore.seedIfEmpty(app)
        if (saved.update_count > 0) runner.weights = saved
    }

    private val banditStore = BanditStore(app)
    private var banditState: BanditState = banditStore.load()
    private val postNudgeObserver = PostNudgeObserver(app)
    private val csvLogger = CSVLogger(app)

    // ── Exposed StateFlows ────────────────────────────────────────────────────

    private val _signals        = MutableStateFlow<BehaviorSignals?>(null)
    val signals: StateFlow<BehaviorSignals?> = _signals.asStateFlow()

    private val _pPhub          = MutableStateFlow(0f)
    val pPhub: StateFlow<Float> = _pPhub.asStateFlow()

    private val _pDrift         = MutableStateFlow(0f)
    val pDrift: StateFlow<Float> = _pDrift.asStateFlow()

    private val _presenceScore  = MutableStateFlow(100f)
    val presenceScore: StateFlow<Float> = _presenceScore.asStateFlow()

    private val _shouldNudge    = MutableStateFlow(false)
    val shouldNudge: StateFlow<Boolean> = _shouldNudge.asStateFlow()

    private val _nudgeFormat    = MutableStateFlow<NudgeFormat?>(null)
    val nudgeFormat: StateFlow<NudgeFormat?> = _nudgeFormat.asStateFlow()

    private val _accuracy       = MutableStateFlow(0f)
    val accuracy: StateFlow<Float> = _accuracy.asStateFlow()

    private val _updateCount    = MutableStateFlow(pipelineRunner.weights.update_count)
    val updateCount: StateFlow<Int> = _updateCount.asStateFlow()

    private val _banditStats    = MutableStateFlow<Map<String, Any>>(emptyMap())
    val banditStats: StateFlow<Map<String, Any>> = _banditStats.asStateFlow()

    private val _featureValues  = MutableStateFlow(FloatArray(FEATURE_NAMES.size) { 0f })
    val featureValues: StateFlow<FloatArray> = _featureValues.asStateFlow()

    private val _categoryBreakdown = MutableStateFlow<List<AppCategoryClassifier.CategoryBreakdown>>(emptyList())
    val categoryBreakdown: StateFlow<List<AppCategoryClassifier.CategoryBreakdown>> = _categoryBreakdown.asStateFlow()

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        // Seed accuracy from historical CSV
        viewModelScope.launch {
            val historical = CSVReader.readAllAsSignalRows(app)
            if (historical.isNotEmpty()) {
                pipelineRunner.run(historical)
                _accuracy.value    = pipelineRunner.currentAccuracy()
                _updateCount.value = pipelineRunner.weights.update_count
                android.util.Log.d("PresenceAI_ML",
                    "Historical: ${historical.size} rows, acc=${(_accuracy.value * 100).toInt()}%")
            }
            _banditStats.value = Bandit.getStats(banditState)
        }

        // Fast path: full pipeline on every heartbeat from MonitoringService
        viewModelScope.launch {
            SignalRepository.latestSignals.collectLatest { latest ->
                latest?.let { processSignals(it) }
            }
        }

        // Live ticker: re-run inference every 1s using latest cached signals
        // Smoothly animates the presence circle without extra device queries
        viewModelScope.launch {
            while (true) {
                delay(1_000L)
                val s = _signals.value ?: continue
                tickScore(s)
            }
        }
    }

    // ── Fast path: full pipeline inference ───────────────────────────────────

    private fun processSignals(s: BehaviorSignals) {
        try {
            val row  = s.toSignalRow()
            val step = pipelineRunner.processRow(row)

            val format: NudgeFormat? = if (step.should_nudge) Bandit.selectAction(banditState) else null

            // Start 45s observation window when a nudge fires
            if (step.should_nudge && format != null) {
                postNudgeObserver.start(format) { resolved ->
                    onLabelResolved(resolved.lrLabel, resolved.banditReward, format)
                }
            }

            val fv = FeatureEngineering.buildFeatureVector(row).asList()

            _signals.value         = s
            _pDrift.value          = step.p_drift.toFloat()
            _pPhub.value           = step.p_phub.toFloat()
            _presenceScore.value   = scoreFromPhub(step.p_phub.toFloat())
            _shouldNudge.value     = step.should_nudge
            _nudgeFormat.value     = format
            _accuracy.value        = pipelineRunner.currentAccuracy()
            _updateCount.value     = pipelineRunner.weights.update_count
            _banditStats.value     = Bandit.getStats(banditState)
            _featureValues.value   = FloatArray(fv.size) { fv[it].toFloat() }
            _categoryBreakdown.value = s.categoryBreakdown

            android.util.Log.d("PresenceAI_ML",
                "Accuracy: ${(_accuracy.value * 100).toInt()}%, P(Phub): ${step.p_phub}")

        } catch (e: Exception) {
            android.util.Log.e("PresenceAI_VM", "processSignals: ${e.message}")
        }
    }

    // ── Live ticker: smooth 1-second score update ─────────────────────────────

    /**
     * Re-runs inference every second on the latest signals.
     * Adds a small time-decay nudge to unlock_count_per_hour so the score
     * drifts upward naturally as time passes without the phone being put down.
     * This is display only — does NOT update model weights.
     */
    private fun tickScore(s: BehaviorSignals) {
        try {
            // Age factor: each second of the 5s window that passes makes unlocks
            // feel slightly more recent. Caps at 1.15× so it doesn't over-inflate.
            val secondsSinceUpdate = ((System.currentTimeMillis() - s.timestamp) / 1_000L)
                .coerceIn(0, 5)
            val ageFactor = 1.0f + (secondsSinceUpdate * 0.03f)  // up to +15%

            val row = s.toSignalRow().copy(
                unlockCountPerHour = (s.unlockCountPerHour * ageFactor).toDouble()
            )
            val fv     = FeatureEngineering.buildFeatureVector(row).asList()
            val pDrift = LrClassifier.predict(fv, pipelineRunner.weights).toFloat()
            val pPhub  = LrClassifier.computePPhub(fv, pipelineRunner.weights, config).toFloat()
            val nudge  = LrClassifier.shouldNudge(fv, pipelineRunner.weights, config)

            _pDrift.value        = pDrift
            _pPhub.value         = pPhub
            _presenceScore.value = scoreFromPhub(pPhub)
            _shouldNudge.value   = nudge

        } catch (e: Exception) {
            // Ticker failures are non-fatal — next tick will retry
        }
    }

    // ── Label resolution (after 45s observation) ─────────────────────────────

    fun onLabelResolved(label: Double, reward: Float, format: NudgeFormat) {
        viewModelScope.launch {
            val s = _signals.value ?: return@launch
            try {
                val labeled = s.toSignalRow().copy(label = label)
                pipelineRunner.processRow(labeled)
                ModelStore.saveWeights(app, pipelineRunner.weights)

                // Write resolved label back into the last CSV row so it's usable for training
                csvLogger.updateLastRowLabel(
                    label        = label,
                    pDrift       = _pDrift.value,
                    presenceScore = _presenceScore.value
                )

                banditState = Bandit.update(banditState, format, reward)
                banditStore.save(banditState)

                _updateCount.value = pipelineRunner.weights.update_count
                _banditStats.value = Bandit.getStats(banditState)
                _accuracy.value    = pipelineRunner.currentAccuracy()

            } catch (e: Exception) {
                android.util.Log.e("PresenceAI_VM", "onLabelResolved: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        postNudgeObserver.stop()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Presence score = (1 - P(phub)) * 100. Clamped [0, 100]. */
    private fun scoreFromPhub(pPhub: Float) = ((1f - pPhub) * 100f).coerceIn(0f, 100f)

    private fun BehaviorSignals.toSignalRow() = SignalRow(
        timestamp                = timestamp,
        userId                   = userId,
        dayNumber                = dayNumber,
        hourOfDay                = hourOfDay,
        isEveningSession         = isEveningSession,
        baselineUnlocksPerHour   = baselineUnlocksPerHour.toDouble(),
        baselineSessionDurationS = baselineSessionDurationS.toDouble(),
        baselineNotifGapS        = baselineNotifGapS.toDouble(),
        unlockCountPerHour       = unlockCountPerHour.toDouble(),
        microSessionDurationS    = microSessionDurationS.toDouble(),
        notifToUnlockGapS        = notifToUnlockGapS.toDouble(),
        behaviorDriftScore       = behaviorDriftScore.toDouble(),
        timePhaseRisk            = timePhaseRisk.toDouble(),
        voiceActivityDetected    = voiceActivityDetected,
        peopleNearbyCount        = peopleNearbyCount,
        vadConfidenceScore       = vadConfidenceScore.toDouble(),
        btSignalStrength         = btSignalStrength.toDouble(),
        label                    = -1.0
    )
}
