package com.nophubbing.presenceai.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nophubbing.presenceai.analytics.BehaviorSignals
import com.nophubbing.presenceai.analytics.SignalRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val _signals =
        MutableStateFlow<BehaviorSignals?>(null)

    val signals: StateFlow<BehaviorSignals?> =
        _signals

    private var lastTimestamp: Long = -1L

    init {
        viewModelScope.launch {

            SignalRepository.latestSignals.collect { latest ->

                if (latest != null && latest.timestamp != lastTimestamp) {

                    lastTimestamp = latest.timestamp
                    _signals.value = latest
                }
            }
        }
    }
}