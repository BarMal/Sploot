package com.sploot.app.settings

enum class WhoopDoubleTapAction {
    NONE,
    START_HAPTICS,
    STOP_HAPTICS,
    TOGGLE_HAPTICS,
}

data class AppSettings(
    val hasCompletedOnboarding: Boolean = false,
    val preferredWhoopDeviceAddress: String? = null,
    val preferredWhoopDeviceName: String? = null,
    val enablePeriodicHistoricalSync: Boolean = true,
    val periodicHistoricalSyncHours: Int = 24,
    val followSystemBatterySaver: Boolean = false,
    val batterySaverDisableWhoopHrStream: Boolean = false,
    val batterySaverDisableWhoopImuStream: Boolean = false,
    val batterySaverDisableWhoopPpgStream: Boolean = true,
    val batterySaverForceGlobalWhoopInterval: Boolean = true,
    val batterySaverGlobalWhoopIntervalSeconds: Int = 5,
    val enableWhoopHrStream: Boolean = true,
    val enableWhoopImuStream: Boolean = true,
    val enableWhoopPpgStream: Boolean = true,
    val globalWhoopCaptureIntervalEnabled: Boolean = false,
    val globalWhoopCaptureIntervalSeconds: Int = 1,
    val imuCaptureIntervalSeconds: Int = 1,
    val ppgCaptureIntervalSeconds: Int = 1,
    val hrCaptureIntervalSeconds: Int = 1,
    val captureWhoopEvents: Boolean = true,
    val whoopDoubleTapAction: WhoopDoubleTapAction = WhoopDoubleTapAction.NONE,
    val enableWhoopUnknownTagPrompts: Boolean = false,
    val runSleepProcessing: Boolean = true,
    val runActivityProcessing: Boolean = true,
    val importHealthConnectSleep: Boolean = true,
    val importHealthConnectActivities: Boolean = true,
    val importHealthConnectHeartRate: Boolean = true,
    val healthConnectLookbackDays: Int = 30,
) {
    fun effectiveImuIntervalSeconds(): Int =
        if (globalWhoopCaptureIntervalEnabled) globalWhoopCaptureIntervalSeconds else imuCaptureIntervalSeconds

    fun effectivePpgIntervalSeconds(): Int =
        if (globalWhoopCaptureIntervalEnabled) globalWhoopCaptureIntervalSeconds else ppgCaptureIntervalSeconds

    fun effectiveHrIntervalSeconds(): Int =
        if (globalWhoopCaptureIntervalEnabled) globalWhoopCaptureIntervalSeconds else hrCaptureIntervalSeconds
}
