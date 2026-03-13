package com.nophubbing.presenceai.analytics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object SignalRepository {

    private val _latestSignals =
        MutableStateFlow<BehaviorSignals?>(null)

    val latestSignals: StateFlow<BehaviorSignals?> =
        _latestSignals

    fun update(signals: BehaviorSignals) {
        _latestSignals.value = signals
    }
}