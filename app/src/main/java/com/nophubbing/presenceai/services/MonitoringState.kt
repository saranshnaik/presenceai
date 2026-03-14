package com.nophubbing.presenceai.services

import kotlinx.coroutines.flow.MutableStateFlow

object MonitoringState {
    val isRunning = MutableStateFlow(false)
}