package com.nophubbing.presenceai.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nophubbing.presenceai.analytics.BehaviorSignals
import com.nophubbing.presenceai.analytics.SignalRepository
import com.nophubbing.presenceai.ml.PipelineConfig
import com.nophubbing.presenceai.ml.PipelineRunner
import com.nophubbing.presenceai.ml.SignalRow
import com.nophubbing.presenceai.storage.CSVReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val _signals = MutableStateFlow<BehaviorSignals?>(null)
    val signals: StateFlow<BehaviorSignals?> = _signals

    private val pipelineConfig = PipelineConfig()
    private val pipelineRunner = PipelineRunner(pipelineConfig)

    private val _pPhub = MutableStateFlow(0f)
    val pPhub: StateFlow<Float> = _pPhub

    private val _pDrift = MutableStateFlow(0f)
    val pDrift: StateFlow<Float> = _pDrift

    private val _shouldNudge = MutableStateFlow(false)
    val shouldNudge: StateFlow<Boolean> = _shouldNudge

    private val _accuracy = MutableStateFlow(0f)
    val accuracy: StateFlow<Float> = _accuracy

    init {
        // Run an initial training pass using the existing dataset to establish accuracy
        viewModelScope.launch {
            val historicalRows = CSVReader.readAllAsSignalRows(getApplication())
            if (historicalRows.isNotEmpty()) {
                val results = pipelineRunner.run(historicalRows)
                _accuracy.value = (results["accuracy"] as? Double)?.toFloat() ?: 0f
            }
        }

        // Observe real-time updates from the Repository
        viewModelScope.launch {
            SignalRepository.latestSignals.collectLatest { latest ->
                latest?.let {
                    _signals.value = it
                    processNewSignals(it)
                }
            }
        }
    }

    private fun processNewSignals(latest: BehaviorSignals) {
        // Map BehaviorSignals to SignalRow for ML Pipeline
        val row = SignalRow(
            timestamp = latest.timestamp,
            userId = latest.userId,
            dayNumber = latest.dayNumber,
            hourOfDay = latest.hourOfDay,
            isEveningSession = latest.isEveningSession,
            baselineUnlocksPerHour = latest.baselineUnlocksPerHour.toDouble(),
            baselineSessionDurationS = latest.baselineSessionDurationS.toDouble(),
            baselineNotifGapS = latest.baselineNotifGapS.toDouble(),
            unlockCountPerHour = latest.unlockCountPerHour.toDouble(),
            microSessionDurationS = latest.microSessionDurationS.toDouble(),
            notifToUnlockGapS = latest.notifToUnlockGapS.toDouble(),
            behaviorDriftScore = latest.behaviorDriftScore.toDouble(),
            timePhaseRisk = latest.timePhaseRisk.toDouble(),
            voiceActivityDetected = 0, // forced to 0
            peopleNearbyCount = 0,     // forced to 0
            vadConfidenceScore = 0.0,  // forced to 0
            btSignalStrength = 0.0,    // forced to 0
            label = -1.0  // Unlabeled real-time — prevents data leakage
        )

        val stepRecord = pipelineRunner.processRow(row)

        _pPhub.value = stepRecord.p_phub.toFloat()
        _pDrift.value = stepRecord.p_drift.toFloat()
        _shouldNudge.value = stepRecord.should_nudge

        // Compute accuracy over labeled data only
        val totalSteps = pipelineRunner.steps
        if (totalSteps.isNotEmpty()) {
            val correct = totalSteps.count {
                if (it.label == -1.0) false
                else (it.p_drift > 0.5 && it.label == 1.0) || (it.p_drift <= 0.5 && it.label == 0.0)
            }
            val labeled = totalSteps.count { it.label != -1.0 }
            if (labeled > 0) {
                _accuracy.value = correct.toFloat() / labeled.toFloat()
            }
        }

        android.util.Log.d("PresenceAI_ML", "Accuracy: ${_accuracy.value * 100}%, P(Phub): ${stepRecord.p_phub}")
    }
}
