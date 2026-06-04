package com.sploot.app.service

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class WhoopRuntimeState {
    IDLE,
    STARTING_LIVE,
    LIVE,
    STARTING_HISTORY,
    HISTORY,
    SWITCHING_TO_LIVE,
    SWITCHING_TO_HISTORY,
    STOPPING,
    ERROR,
}

@Singleton
class WhoopRuntimeCoordinator @Inject constructor() {
    private val _state = MutableStateFlow(WhoopRuntimeState.IDLE)
    val state: StateFlow<WhoopRuntimeState> = _state.asStateFlow()

    fun setState(state: WhoopRuntimeState) {
        _state.value = state
    }
}
