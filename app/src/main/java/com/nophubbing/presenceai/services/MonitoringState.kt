package com.nophubbing.presenceai.services

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object MonitoringState {

    var isRunning: Boolean by mutableStateOf(false)

}