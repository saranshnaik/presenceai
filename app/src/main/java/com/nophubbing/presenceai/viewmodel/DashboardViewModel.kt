package com.nophubbing.presenceai.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nophubbing.presenceai.analytics.BehaviorSignals
import com.nophubbing.presenceai.storage.CSVReader
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val _signals =
        MutableStateFlow<BehaviorSignals?>(null)

    val signals: StateFlow<BehaviorSignals?> =
        _signals

    init {

        viewModelScope.launch {

            while (true) {

                val latest =
                    CSVReader.readLatestSignals(getApplication())

                if (latest != null) {
                    _signals.value = latest
                }

                delay(5000)   // refresh every 5s
            }
        }
    }
}