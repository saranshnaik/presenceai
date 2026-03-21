package com.nophubbing.presenceai.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nophubbing.presenceai.analytics.AppCategoryClassifier
import com.nophubbing.presenceai.analytics.BehaviorSignals
import com.nophubbing.presenceai.analytics.InsightsRepository
import com.nophubbing.presenceai.analytics.SignalRepository
import com.nophubbing.presenceai.genai.*
import com.nophubbing.presenceai.integration.PostNudgeObserver
import com.nophubbing.presenceai.ml.*
import com.nophubbing.presenceai.rl.Bandit
import com.nophubbing.presenceai.rl.BanditState
import com.nophubbing.presenceai.rl.BanditStore
import com.nophubbing.presenceai.rl.NudgeFormat
import com.nophubbing.presenceai.services.*
import com.nophubbing.presenceai.storage.CSVLogger
import com.nophubbing.presenceai.storage.CSVReader
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * DashboardViewModel — single source of truth.
 *
 * Full pipeline:
 *  ML (RuleBasedClassifier) → P(phub)
 *  → RL (ε-Greedy Bandit) → HAPTIC or NOTIFICATION
 *  → Gemini AI → nudge copy
 *  → Deliver (vibration + notification with Yes/No)
 *  → FeedbackActivityMonitor (button tap → LR update + bandit reward)
 *  → PostNudgeObserver (45s screen watch → bandit reward backup)
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application

    // ── ML ─────────────────────────────────────────────────────────────────────
    private val pipelineRunner = PipelineRunner().also { runner ->
        val saved = ModelStore.seedIfEmpty(app)
        if (saved.update_count > 0) runner.weights = saved
    }

    // ── RL ─────────────────────────────────────────────────────────────────────
    private val banditStore = BanditStore(app)
    private var banditState: BanditState = banditStore.load()

    // ── Infrastructure ─────────────────────────────────────────────────────────
    private val postNudgeObserver = PostNudgeObserver(app)
    private val nudgingSystem     = NudgingSystem(app)
    private val insightCache      = InsightCache(app)
    private val csvLogger         = CSVLogger(app)

    private var lastNudgeFormat:  NudgeFormat?    = null
    private var lastNudgeSignals: BehaviorSignals? = null
    private var nudgeInFlight                      = false

    // ── LR online-learner adapter ──────────────────────────────────────────────
    private val onlineLearnerAdapter = object : OnlineLearnerPort {
        override fun update(features: FloatArray, label: Int) {
            try {
                pipelineRunner.weights = OnlineLearner.update(
                    features.map { it.toDouble() }, label.toDouble(), pipelineRunner.weights
                )
                ModelStore.saveWeights(app, pipelineRunner.weights)
                _updateCount.value = pipelineRunner.weights.update_count
                Log.d("PAI_VM", "✅ LR update label=$label → updates=${pipelineRunner.weights.update_count}")
            } catch (e: Exception) {
                Log.e("PAI_VM", "LR update: ${e.message}")
            }
        }
    }

    private val signalProvider = object : SignalProvider {
        override suspend fun captureSnapshot() = FeedbackActivityMonitor.SignalSnapshot(
            unlockCount   = _signals.value?.unlocks ?: 0,
            sessionCount  = _signals.value?.totalSessions ?: 0,
            phubbingScore = _pPhub.value * 100f,
            notifReflexes = _signals.value?.notificationReflexCount ?: 0,
            features      = _featureValues.value
        )
    }

    // ── StateFlows ─────────────────────────────────────────────────────────────
    private val _signals            = MutableStateFlow<BehaviorSignals?>(null)
    val signals: StateFlow<BehaviorSignals?>    = _signals.asStateFlow()

    private val _pPhub              = MutableStateFlow(0f)
    val pPhub: StateFlow<Float>     = _pPhub.asStateFlow()

    private val _pDrift             = MutableStateFlow(0f)
    val pDrift: StateFlow<Float>    = _pDrift.asStateFlow()

    private val _presenceScore      = MutableStateFlow(100f)
    val presenceScore: StateFlow<Float> = _presenceScore.asStateFlow()

    private val _shouldNudge        = MutableStateFlow(false)
    val shouldNudge: StateFlow<Boolean> = _shouldNudge.asStateFlow()

    private val _nudgeFormat        = MutableStateFlow<NudgeFormat?>(null)
    val nudgeFormat: StateFlow<NudgeFormat?>    = _nudgeFormat.asStateFlow()

    private val _nudgeCopy          = MutableStateFlow(Fallbacks.NUDGE_COPY)
    val nudgeCopy: StateFlow<String> = _nudgeCopy.asStateFlow()

    private val _nudgeExplanation   = MutableStateFlow<String?>(null)
    val nudgeExplanation: StateFlow<String?>    = _nudgeExplanation.asStateFlow()

    private val _weeklyInsight      = MutableStateFlow<String?>(null)
    val weeklyInsight: StateFlow<String?>       = _weeklyInsight.asStateFlow()

    private val _isLoadingInsight   = MutableStateFlow(false)
    val isLoadingInsight: StateFlow<Boolean>    = _isLoadingInsight.asStateFlow()

    private val _isLoadingExplain   = MutableStateFlow(false)
    val isLoadingExplain: StateFlow<Boolean>    = _isLoadingExplain.asStateFlow()

    private val _updateCount        = MutableStateFlow(pipelineRunner.weights.update_count)
    val updateCount: StateFlow<Int> = _updateCount.asStateFlow()

    private val _banditStats        = MutableStateFlow<Map<String, Any>>(emptyMap())
    val banditStats: StateFlow<Map<String, Any>> = _banditStats.asStateFlow()

    private val _featureValues      = MutableStateFlow(FloatArray(7) { 0f })
    val featureValues: StateFlow<FloatArray>    = _featureValues.asStateFlow()

    private val _voiceLevel         = MutableStateFlow(0f)
    val voiceLevel: StateFlow<Float> = _voiceLevel.asStateFlow()

    private val _proximityStrength  = MutableStateFlow(0f)
    val proximityStrength: StateFlow<Float> = _proximityStrength.asStateFlow()

    private val _categoryBreakdown  = MutableStateFlow<List<AppCategoryClassifier.CategoryBreakdown>>(emptyList())
    val categoryBreakdown: StateFlow<List<AppCategoryClassifier.CategoryBreakdown>> = _categoryBreakdown.asStateFlow()

    // ── Init ───────────────────────────────────────────────────────────────────
    init {
        nudgingSystem.start()

        FeedbackActivityMonitor.init(
            learner  = onlineLearnerAdapter,
            provider = signalProvider,
            onReward = { reward ->
                val format = FeedbackActivityMonitor.getInstance()?.lastNudgeFormat
                if (format != null && reward != 0.0f) {
                    viewModelScope.launch { applyBanditReward(reward, format) }
                }
            }
        )

        viewModelScope.launch {
            CSVReader.readAllAsSignalRows(app).let { rows ->
                if (rows.isNotEmpty()) pipelineRunner.run(rows)
            }
            _updateCount.value = pipelineRunner.weights.update_count
            _banditStats.value = Bandit.getStats(banditState)
            Log.d("PAI_VM", "Init: haptic=${banditState.hapticCount} notif=${banditState.notifCount}")
        }

        viewModelScope.launch {
            SignalRepository.latestSignals.collectLatest { it?.let { s -> processSignals(s) } }
        }

        viewModelScope.launch {
            while (true) { delay(1_000L); _signals.value?.let { tickScore(it) } }
        }

        viewModelScope.launch {
            insightCache.getWeeklyInsight()?.let { _weeklyInsight.value = it }
        }
    }

    // ── Fast path (every heartbeat from MonitoringService) ────────────────────
    private fun processSignals(s: BehaviorSignals) {
        try {
            val row  = s.toSignalRow()
            val step = pipelineRunner.processRow(row)
            val fv   = FeatureEngineering.buildFeatureVector(row)

            _signals.value           = s
            _pDrift.value            = step.p_drift.toFloat()
            _pPhub.value             = step.p_phub.toFloat()
            _presenceScore.value     = scoreFromPhub(step.p_phub.toFloat())
            _shouldNudge.value       = step.should_nudge
            _updateCount.value       = pipelineRunner.weights.update_count
            _banditStats.value       = Bandit.getStats(banditState)
            _featureValues.value     = fv
            _categoryBreakdown.value = s.categoryBreakdown
            _voiceLevel.value        = s.vadConfidenceScore
            _proximityStrength.value = ((s.btSignalStrength + 100f) / 0.6f).coerceIn(0f, 100f)

            Log.d("PAI_VM", "signals: pPhub=${step.p_phub} nudge=${step.should_nudge} inFlight=$nudgeInFlight")

            // ── NUDGE PIPELINE ──────────────────────────────────────────
            if (step.should_nudge && !nudgeInFlight) {
                nudgeInFlight = true
                lastNudgeSignals = s
                _nudgeExplanation.value = null

                // RL: pick format
                val format = Bandit.selectAction(banditState)
                lastNudgeFormat = format
                _nudgeFormat.value = format
                FeedbackActivityMonitor.getInstance()?.lastNudgeFormat = format

                Log.d("PAI_VM", "🔔 Nudge firing: format=$format pPhub=${step.p_phub}")

                viewModelScope.launch {
                    // 1. Gemini generates nudge copy
                    val copy = try {
                        NudgeCopyGenerator.generateNudgeCopy(
                            ContextBuilder.buildNudgeContext(s, step.p_phub.toFloat())
                        )
                    } catch (_: Exception) { Fallbacks.NUDGE_COPY }
                    _nudgeCopy.value = copy

                    // 2. Deliver: vibration + notification with Yes/No buttons
                    nudgingSystem.deliverNudge(format, copy)

                    // 3. PostNudgeObserver: 45s screen-watch for bandit reward backup
                    postNudgeObserver.start(format) { resolved ->
                        Log.d("PAI_VM", "📊 PostNudge: lr=${resolved.lrLabel} reward=${resolved.banditReward}")
                        if (resolved.banditReward != 0.0f) {
                            viewModelScope.launch { applyBanditReward(resolved.banditReward, resolved.format) }
                        }
                        if (resolved.lrLabel != -1.0) {
                            _signals.value?.let { sig ->
                                viewModelScope.launch { applyLRLabel(sig, resolved.lrLabel) }
                            }
                        }
                        nudgeInFlight = false
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PAI_VM", "processSignals: ${e.message}", e)
        }
    }

    // ── Bandit reward ─────────────────────────────────────────────────────────
    private suspend fun applyBanditReward(reward: Float, format: NudgeFormat) {
        try {
            banditState = Bandit.update(banditState, format, reward)
            banditStore.save(banditState)
            _banditStats.value = Bandit.getStats(banditState)
            Log.d("PAI_VM", "✅ Bandit: format=$format reward=$reward " +
                "haptic(${banditState.hapticCount}, avg=${"%.2f".format(banditState.hapticAvg)}) " +
                "notif(${banditState.notifCount}, avg=${"%.2f".format(banditState.notifAvg)})")
        } catch (e: Exception) {
            Log.e("PAI_VM", "applyBanditReward: ${e.message}")
        }
    }

    // ── LR label ─────────────────────────────────────────────────────────────
    private suspend fun applyLRLabel(s: BehaviorSignals, label: Double) {
        try {
            pipelineRunner.processRow(s.toSignalRow().copy(label = label))
            ModelStore.saveWeights(app, pipelineRunner.weights)
            csvLogger.updateLastRowLabel(label, _pDrift.value, _presenceScore.value)
            _updateCount.value = pipelineRunner.weights.update_count
            insightCache.invalidate()
        } catch (e: Exception) {
            Log.e("PAI_VM", "applyLRLabel: ${e.message}")
        }
    }

    // ── Compat shim for PostNudgeObserver ──────────────────────────────────────
    fun onLabelResolved(label: Double, reward: Float, format: NudgeFormat) {
        viewModelScope.launch {
            if (reward != 0.0f) applyBanditReward(reward, format)
            _signals.value?.let { if (label != -1.0) applyLRLabel(it, label) }
        }
    }

    // ── Live ticker ────────────────────────────────────────────────────────────
    private fun tickScore(s: BehaviorSignals) {
        try {
            val fv  = FeatureEngineering.buildFeatureVector(s.toSignalRow())
            val fvL = fv.map { it.toDouble() }
            _pDrift.value        = LrClassifier.predict(fvL, pipelineRunner.weights).toFloat()
            _pPhub.value         = LrClassifier.computePPhub(fvL, pipelineRunner.weights, PipelineConfig).toFloat()
            _presenceScore.value = scoreFromPhub(_pPhub.value)
            _shouldNudge.value   = LrClassifier.shouldNudge(fvL, pipelineRunner.weights, PipelineConfig)
            _featureValues.value = fv
        } catch (_: Exception) {}
    }

    // ── Gemini AI weekly insight ───────────────────────────────────────────────
    fun generateWeeklyInsight() {
        viewModelScope.launch {
            _isLoadingInsight.value = true
            try {
                val daily = InsightsRepository.getDailySummary(app)
                val bs    = banditState
                val stats = WeeklyStats(
                    avgPresenceScore     = daily.lastOrNull()?.avgPresenceScore ?: 75f,
                    totalNudges          = _updateCount.value,
                    nudgesResponded      = bs.hapticCount + bs.notifCount,
                    preferredFormat      = (_banditStats.value["preferred_arm"] as? String) ?: "HAPTIC",
                    hapticAvgReward      = if (bs.hapticCount > 0) bs.hapticTotalReward / bs.hapticCount else 0f,
                    notifAvgReward       = if (bs.notifCount > 0) bs.notifTotalReward / bs.notifCount else 0f,
                    hapticTrials         = bs.hapticCount,
                    notifTrials          = bs.notifCount,
                    avgUnlocksPerHour    = _signals.value?.unlockCountPerHour ?: 0f,
                    avgMicroSessionRatio = daily.lastOrNull()?.microSessionRatio ?: 0f,
                    peakRiskHour         = _signals.value?.hourOfDay ?: 20,
                    daysTracked          = daily.size.coerceAtLeast(1)
                )
                val insight = WeeklyInsightGenerator.generateWeeklyInsight(app, stats)
                _weeklyInsight.value = insight
                insightCache.putWeeklyInsight(insight)
            } catch (e: Exception) {
                Log.e("PAI_VM", "generateWeeklyInsight: ${e.message}")
                _weeklyInsight.value = Fallbacks.WEEKLY_INSIGHT
            } finally {
                _isLoadingInsight.value = false
            }
        }
    }

    // ── Gemini AI nudge explanation ────────────────────────────────────────────
    fun explainLastNudge() {
        val s = lastNudgeSignals ?: return
        val f = lastNudgeFormat  ?: return
        viewModelScope.launch {
            _isLoadingExplain.value = true
            try {
                _nudgeExplanation.value = NudgeExplainGenerator.explainNudge(
                    app, ContextBuilder.buildNudgeEvent(s, _pPhub.value, f.name, _pDrift.value)
                )
            } catch (_: Exception) {
                _nudgeExplanation.value = Fallbacks.NUDGE_EXPLAIN
            } finally {
                _isLoadingExplain.value = false
            }
        }
    }

    fun dismissNudgeExplanation() { _nudgeExplanation.value = null }

    private fun scoreFromPhub(p: Float) = ((1f - p) * 100f).coerceIn(0f, 100f)

    private fun BehaviorSignals.toSignalRow() = SignalRow(
        timestamp = timestamp, userId = userId, dayNumber = dayNumber,
        hourOfDay = hourOfDay, isEveningSession = isEveningSession,
        baselineUnlocksPerHour = baselineUnlocksPerHour.toDouble(),
        baselineSessionDurationS = baselineSessionDurationS.toDouble(),
        baselineNotifGapS = baselineNotifGapS.toDouble(),
        unlockCountPerHour = unlockCountPerHour.toDouble(),
        microSessionDurationS = microSessionDurationS.toDouble(),
        notifToUnlockGapS = notifToUnlockGapS.toDouble(),
        behaviorDriftScore = behaviorDriftScore.toDouble(),
        timePhaseRisk = timePhaseRisk.toDouble(),
        voiceActivityDetected = voiceActivityDetected,
        peopleNearbyCount = peopleNearbyCount,
        vadConfidenceScore = vadConfidenceScore.toDouble(),
        btSignalStrength = btSignalStrength.toDouble(),
        label = -1.0,
        unlocks10Min      = rawUnlocks,
        rollingAvgUnlocks = baselineUnlocksPerHour.coerceAtLeast(0.1f),
        totalSessions     = totalSessions,
        microSessions     = microSessions,
        lastNotifDeltaMs  = if (notificationReflexCount > 0) 4_000L else 120_000L,
        behaviorRate      = unlockCountPerHour,
        baselineMean      = baselineUnlocksPerHour,
        baselineStdDev    = (baselineUnlocksPerHour * 0.3f).coerceAtLeast(0.1f),
        baselineReady     = dayNumber >= 3
    )

    override fun onCleared() {
        super.onCleared()
        postNudgeObserver.stop()
        nudgingSystem.destroy()
        FeedbackActivityMonitor.getInstance()?.destroy()
    }
}
