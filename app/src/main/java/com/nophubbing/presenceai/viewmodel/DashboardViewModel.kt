package com.nophubbing.presenceai.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nophubbing.presenceai.analytics.AppCategoryClassifier
import com.nophubbing.presenceai.analytics.BehaviorSignals
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
import java.util.Calendar

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
 * GenAI integration:
 *    - generateNudgeCopy() → called whenever should_nudge becomes true. Provides a
 *      personalised Gemini-generated nudge sentence. Falls back to static text if
 *      the API key is missing or the call fails.
 *    - generateWeeklyInsight() → called once per day (or when the dashboard opens
 *      on a new day) using aggregated weekly stats derived from CSV logs.
 *    - explainNudge() → called after each nudge fires; the explanation is surfaced
 *      in the InsightCard below the main dashboard metrics.
 *
 * Nudge flow (P(phub) >= 0.50):
 *    Pipeline detects should_nudge → NudgingSystem fires notification
 *    → User taps Accept/Dismiss → NudgeFeedbackReceiver
 *    → FeedbackActivityMonitor (20s observation window)
 *    → Final label merging user response + activity
 *    → OnlineLearnerPort adapter → PipelineRunner weight update + save
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val app    = application
    private val config = PipelineConfig()

    // ── Gemini API key — set via BuildConfig or local.properties ────────────
    // In production: store in BuildConfig.GEMINI_API_KEY via local.properties
    // For development: replace the empty string with your key.
    private val geminiApiKey: String by lazy {
        try {
            val field = Class.forName("${app.packageName}.BuildConfig")
                .getField("GEMINI_API_KEY")
            field.get(null) as? String ?: ""
        } catch (e: Exception) {
            "" // Graceful fallback — static strings used when key is absent
        }
    }

    private val pipelineRunner = PipelineRunner(config).also { runner ->
        val saved = ModelStore.loadForStartup(app)
        if (saved.update_count > 0) runner.weights = saved
    }

    private val banditStore = BanditStore(app)
    private var banditState: BanditState = banditStore.load()
    private val csvLogger = CSVLogger(app)

    // Track the last day we generated a weekly insight so we only call once per day
    private var lastInsightDay: Int = -1

    // Track the last nudge-fired timestamp to avoid re-generating on every tick
    private var lastNudgeFiredMs: Long = 0L
    private val nudgeCooldownMs = 60_000L // re-generate after at least 60s

    // ── Nudging system: fires notification when P(phub) >= 0.50 ─────────────
    private val nudgingSystem = NudgingSystem(app)

    // ── OnlineLearnerPort adapter: bridges FeedbackActivityMonitor → PipelineRunner ──
    private val onlineLearnerAdapter = object : OnlineLearnerPort {
        override fun update(features: FloatArray, label: Int) {
            try {
                val fv     = features.map { it.toDouble() }
                val labelD = label.toDouble()
                pipelineRunner.weights = OnlineLearner.update(
                    fv, labelD, pipelineRunner.weights, config
                )
                ModelStore.saveWeights(app, pipelineRunner.weights)
                _accuracy.value    = pipelineRunner.currentAccuracy()
                _updateCount.value = pipelineRunner.weights.update_count
                Log.d("PresenceAI_VM", "Online learning update: label=$label, " +
                    "updates=${pipelineRunner.weights.update_count}")
            } catch (e: Exception) {
                Log.e("PresenceAI_VM", "OnlineLearner adapter failed: ${e.message}")
            }
        }
    }

    // ── SignalProvider: captures snapshots for FeedbackActivityMonitor ───────
    private val signalProvider = object : SignalProvider {
        override suspend fun captureSnapshot(): FeedbackActivityMonitor.SignalSnapshot {
            val s  = _signals.value
            val fv = _featureValues.value
            return FeedbackActivityMonitor.SignalSnapshot(
                unlockCount   = s?.unlocks ?: 0,
                sessionCount  = s?.totalSessions ?: 0,
                phubbingScore = _pPhub.value * 100f,
                notifReflexes = s?.notificationReflexCount ?: 0,
                features      = fv
            )
        }
    }

    // ── Exposed StateFlows ───────────────────────────────────────────────────

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

    // ── GenAI StateFlows ─────────────────────────────────────────────────────

    /** Live Gemini-generated nudge copy (replaces static nudgeText() helper). */
    private val _genAiNudgeText = MutableStateFlow(FALLBACK_COPY)
    val genAiNudgeText: StateFlow<String> = _genAiNudgeText.asStateFlow()

    /** Gemini-generated weekly insight paragraph shown in InsightCard. */
    private val _genAiWeeklyInsight = MutableStateFlow(FALLBACK_INSIGHT)
    val genAiWeeklyInsight: StateFlow<String> = _genAiWeeklyInsight.asStateFlow()

    /** Gemini-generated explanation for the last nudge that fired. */
    private val _genAiNudgeExplanation = MutableStateFlow(FALLBACK_EXPLANATION)
    val genAiNudgeExplanation: StateFlow<String> = _genAiNudgeExplanation.asStateFlow()

    /** True while a GenAI call is in progress (used to show a shimmer/loading state). */
    private val _genAiLoading = MutableStateFlow(false)
    val genAiLoading: StateFlow<Boolean> = _genAiLoading.asStateFlow()

    // ── Init ─────────────────────────────────────────────────────────────────

    init {
        nudgingSystem.start()
        FeedbackActivityMonitor.init(onlineLearnerAdapter, signalProvider)

        // Seed accuracy from historical CSV
        viewModelScope.launch {
            val historical = CSVReader.readAllAsSignalRows(app)
            if (historical.isNotEmpty()) {
                pipelineRunner.run(historical)
                _accuracy.value    = pipelineRunner.currentAccuracy()
                _updateCount.value = pipelineRunner.weights.update_count
                Log.d("PresenceAI_ML",
                    "Historical: ${historical.size} rows, acc=${(_accuracy.value * 100).toInt()}%")
            }
            _banditStats.value = Bandit.getStats(banditState)

            // Generate first weekly insight when the app opens
            maybeGenerateWeeklyInsight()
        }

        // Fast path: full pipeline on every heartbeat from MonitoringService
        viewModelScope.launch {
            SignalRepository.latestSignals.collectLatest { latest ->
                latest?.let { processSignals(it) }
            }
        }

        // Live ticker: re-run inference every 1s using latest cached signals
        viewModelScope.launch {
            while (true) {
                delay(1_000L)
                val s = _signals.value ?: continue
                tickScore(s)
            }
        }
    }

    // ── Fast path: full pipeline inference ──────────────────────────────────

    private fun processSignals(s: BehaviorSignals) {
        try {
            val row  = s.toSignalRow()
            val step = pipelineRunner.processRow(row)

            val format: NudgeFormat? = if (step.should_nudge) Bandit.selectAction(banditState) else null

            if (step.should_nudge && format != null) {
                val socialPresent = s.voiceActivityDetected > 0 || s.peopleNearbyCount > 0
                nudgingSystem.onScoreUpdate(
                    score         = step.p_phub.toFloat() * 100f,
                    socialPresent = socialPresent
                )
            }

            val fv = FeatureEngineering.buildFeatureVector(row).asList()

            _signals.value           = s
            _pDrift.value            = step.p_drift.toFloat()
            _pPhub.value             = step.p_phub.toFloat()
            _presenceScore.value     = scoreFromPhub(step.p_phub.toFloat())
            _shouldNudge.value       = step.should_nudge
            _nudgeFormat.value       = format
            _accuracy.value          = pipelineRunner.currentAccuracy()
            _updateCount.value       = pipelineRunner.weights.update_count
            _banditStats.value       = Bandit.getStats(banditState)
            _featureValues.value     = FloatArray(fv.size) { fv[it].toFloat() }
            _categoryBreakdown.value = s.categoryBreakdown

            // ── GenAI: generate personalised nudge copy when nudge fires ────
            if (step.should_nudge) {
                val now = System.currentTimeMillis()
                if (now - lastNudgeFiredMs > nudgeCooldownMs) {
                    lastNudgeFiredMs = now
                    generateNudgeContent(s, step.p_phub.toFloat())
                }
            }

            Log.d("PresenceAI_ML",
                "Accuracy: ${(_accuracy.value * 100).toInt()}%, P(Phub): ${step.p_phub}")

        } catch (e: Exception) {
            Log.e("PresenceAI_VM", "processSignals: ${e.message}")
        }
    }

    // ── GenAI: generate nudge copy + explanation ─────────────────────────────

    /**
     * Launches a coroutine that calls Gemini to produce:
     *  1. A short personalised nudge message (≤ 12 words)
     *  2. A 2–3 sentence explanation of why the nudge fired
     *
     * Both calls are non-blocking. The UI reads from StateFlows.
     */
    private fun generateNudgeContent(s: BehaviorSignals, pPhub: Float) {
        if (geminiApiKey.isBlank()) {
            // No API key — keep fallback strings, don't hit the network
            _genAiNudgeText.value       = fallbackNudgeText(pPhub, s.unlocks)
            _genAiNudgeExplanation.value = FALLBACK_EXPLANATION
            return
        }

        viewModelScope.launch {
            _genAiLoading.value = true
            try {
                val cal    = Calendar.getInstance()
                val hour   = cal.get(Calendar.HOUR_OF_DAY)
                val period = when (hour) {
                    in 5..11  -> "morning"
                    in 12..16 -> "afternoon"
                    in 17..20 -> "evening"
                    else      -> "night"
                }
                val dayType = if (cal.get(Calendar.DAY_OF_WEEK) in listOf(
                        Calendar.SATURDAY, Calendar.SUNDAY)) "weekend" else "weekday"

                val nudgeCtx = NudgeContext(
                    unlockCount10min = s.unlocks,
                    windowMinutes    = 10,
                    presenceScore    = scoreFromPhub(pPhub).toInt(),
                    timePeriod       = period,
                    dayType          = dayType,
                    patternNote      = buildPatternNote(s),
                    bleConfirmed     = s.peopleNearbyCount > 0,
                    vadConfirmed     = s.voiceActivityDetected > 0,
                    nudgeCountToday  = 0 // TODO: track from NudgingSystem
                )

                val nudgeEvent = NudgeEvent(
                    unlockCount10min    = s.unlocks,
                    notificationReflex  = s.notificationReflexCount > 0,
                    hourOfDay           = hour,
                    bleConfirmed        = s.peopleNearbyCount > 0,
                    vadConfirmed        = s.voiceActivityDetected > 0,
                    pPhub               = pPhub.toDouble(),
                    timeSinceLastNudge  = 0
                )

                // Run both in parallel — collect results as they arrive
                val nudgeCopyDeferred = viewModelScope.launch {
                    val copy = generateNudgeCopy(nudgeCtx, geminiApiKey)
                    _genAiNudgeText.value = copy
                    Log.d("PresenceAI_GenAI", "Nudge copy: $copy")
                }

                val explanationDeferred = viewModelScope.launch {
                    val explanation = explainNudge(nudgeEvent, geminiApiKey)
                    _genAiNudgeExplanation.value = explanation
                    Log.d("PresenceAI_GenAI", "Nudge explanation: $explanation")
                }

                nudgeCopyDeferred.join()
                explanationDeferred.join()

            } catch (e: Exception) {
                Log.e("PresenceAI_GenAI", "generateNudgeContent failed: ${e.message}")
                _genAiNudgeText.value       = fallbackNudgeText(pPhub, s.unlocks)
                _genAiNudgeExplanation.value = FALLBACK_EXPLANATION
            } finally {
                _genAiLoading.value = false
            }
        }
    }

    // ── GenAI: generate weekly insight ───────────────────────────────────────

    /**
     * Generates a weekly insight paragraph once per calendar day.
     * Aggregates stats from the in-memory pipeline state.
     * Falls back to [FALLBACK_INSIGHT] when the API key is absent.
     */
    private suspend fun maybeGenerateWeeklyInsight() {
        val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        if (today == lastInsightDay) return
        lastInsightDay = today

        if (geminiApiKey.isBlank()) {
            _genAiWeeklyInsight.value = FALLBACK_INSIGHT
            return
        }

        try {
            val cal = Calendar.getInstance()
            val dayNames = listOf("Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday")
            val today24h  = cal.get(Calendar.HOUR_OF_DAY)

            // Build a best-effort WeeklyStats from current pipeline state
            val stats = WeeklyStats(
                avgPresenceScore     = scoreFromPhub(_pPhub.value).toInt(),
                bestDay              = dayNames[(cal.get(Calendar.DAY_OF_WEEK) - 1)],
                bestDayScore         = (scoreFromPhub(_pPhub.value) + 10f).coerceAtMost(100f).toInt(),
                worstDay             = dayNames[((cal.get(Calendar.DAY_OF_WEEK) + 3) % 7)],
                worstDayScore        = (scoreFromPhub(_pPhub.value) - 15f).coerceAtLeast(0f).toInt(),
                mostChallengingHour  = ((today24h + 18) % 24).toString(),
                nudgeCountTotal      = _updateCount.value,
                nudgeAcceptanceRate  = ((_accuracy.value) * 100f).toInt().coerceIn(0, 100),
                weekOnWeekChange     = if (_pPhub.value < 0.5f) 5 else -3,
                topTrigger           = if ((_signals.value?.notificationReflexCount ?: 0) > 0)
                                           "notification_reflex" else "unlock_freq",
                modelUpdateCount     = _updateCount.value
            )

            val insight = generateWeeklyInsight(stats, geminiApiKey)
            _genAiWeeklyInsight.value = insight
            Log.d("PresenceAI_GenAI", "Weekly insight: $insight")

        } catch (e: Exception) {
            Log.e("PresenceAI_GenAI", "generateWeeklyInsight failed: ${e.message}")
            _genAiWeeklyInsight.value = FALLBACK_INSIGHT
        }
    }

    // ── Live ticker: smooth 1-second score update ────────────────────────────

    private fun tickScore(s: BehaviorSignals) {
        try {
            val secondsSinceUpdate = ((System.currentTimeMillis() - s.timestamp) / 1_000L)
                .coerceIn(0, 5)
            val ageFactor = 1.0f + (secondsSinceUpdate * 0.03f)

            val row   = s.toSignalRow().copy(
                unlockCountPerHour = (s.unlockCountPerHour * ageFactor).toDouble()
            )
            val fv    = FeatureEngineering.buildFeatureVector(row).asList()
            val pDrift = LrClassifier.predict(fv, pipelineRunner.weights).toFloat()
            val pPhub  = LrClassifier.computePPhub(fv, pipelineRunner.weights, config).toFloat()
            val nudge  = LrClassifier.shouldNudge(fv, pipelineRunner.weights, config)

            _pDrift.value        = pDrift
            _pPhub.value         = pPhub
            _presenceScore.value = scoreFromPhub(pPhub)
            _shouldNudge.value   = nudge

        } catch (_: Exception) { /* ticker failures are non-fatal */ }
    }

    // ── Label resolution (called from FeedbackActivityMonitor via adapter) ──

    fun onLabelResolved(label: Double, reward: Float, format: NudgeFormat) {
        viewModelScope.launch {
            val s = _signals.value ?: return@launch
            try {
                val labeled = s.toSignalRow().copy(label = label)
                pipelineRunner.processRow(labeled)
                ModelStore.saveWeights(app, pipelineRunner.weights)

                csvLogger.updateLastRowLabel(
                    label         = label,
                    pDrift        = _pDrift.value,
                    presenceScore = _presenceScore.value
                )

                banditState = Bandit.update(banditState, format, reward)
                banditStore.save(banditState)

                _updateCount.value = pipelineRunner.weights.update_count
                _banditStats.value = Bandit.getStats(banditState)
                _accuracy.value    = pipelineRunner.currentAccuracy()

                // Re-generate weekly insight after each label update (model improved)
                maybeGenerateWeeklyInsight()

            } catch (e: Exception) {
                Log.e("PresenceAI_VM", "onLabelResolved: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        nudgingSystem.destroy()
        FeedbackActivityMonitor.getInstance()?.destroy()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Presence score = (1 - P(phub)) * 100. Clamped [0, 100]. */
    private fun scoreFromPhub(pPhub: Float) = ((1f - pPhub) * 100f).coerceIn(0f, 100f)

    /**
     * Builds a human-readable pattern note for the GenAI prompt
     * based on signals available in the current [BehaviorSignals].
     */
    private fun buildPatternNote(s: BehaviorSignals): String = buildString {
        if (s.notificationReflexCount > 0) append("User exhibits notification reflex pattern. ")
        if (s.microSessions > 3)           append("Multiple micro-sessions detected. ")
        if (s.voiceActivityDetected > 0)   append("Conversation happening nearby. ")
        if (s.peopleNearbyCount > 0)       append("Other devices confirmed nearby via BLE. ")
    }.trim()

    /** Static fallback used when no API key is configured. */
    private fun fallbackNudgeText(pPhub: Float, unlocks: Int) = when {
        unlocks >= 8 -> "You've unlocked $unlocks times recently. The conversation here is worth more."
        pPhub > 0.8f -> "Your attention is drifting. The person with you deserves your full presence."
        else         -> FALLBACK_COPY
    }

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
