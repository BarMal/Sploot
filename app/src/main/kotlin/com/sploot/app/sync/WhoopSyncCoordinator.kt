package com.sploot.app.sync

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class WhoopSyncCoordinator @Inject constructor() {
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    fun markSyncStarted() {
        _isSyncing.value = true
    }

    fun markSyncFinished() {
        _isSyncing.value = false
    }
}
