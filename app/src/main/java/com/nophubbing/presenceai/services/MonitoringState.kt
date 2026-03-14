package com.nophubbing.presenceai.services

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * MonitoringState — singleton that exposes whether MonitoringService is running.
 * Written by MonitoringService.onCreate/onDestroy.
 * Read by DashboardScreen via StateFlow.collectAsState().
 */
object MonitoringState {
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    fun setRunning(running: Boolean) {
        _isRunning.value = running
    }
}