package com.sploot.app.settings

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class AppSettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun current(): AppSettings = _settings.value

    fun update(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        persist(updated)
    }

    fun lastAppOpenHistoricalSyncTriggerAtMs(): Long =
        prefs.getLong(KEY_LAST_APP_OPEN_SYNC_TRIGGER_MS, 0L)

    fun markAppOpenHistoricalSyncTriggered(nowMs: Long) {
        prefs.edit()
            .putLong(KEY_LAST_APP_OPEN_SYNC_TRIGGER_MS, nowMs)
            .apply()
    }

    private fun load() = AppSettings(
        hasCompletedOnboarding = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false),
        preferredWhoopDeviceAddress = prefs.getString(KEY_PREFERRED_DEVICE_ADDRESS, null),
        preferredWhoopDeviceName = prefs.getString(KEY_PREFERRED_DEVICE_NAME, null),
        enablePeriodicHistoricalSync = prefs.getBoolean(KEY_ENABLE_PERIODIC_SYNC, true),
        periodicHistoricalSyncHours = prefs.getInt(KEY_PERIODIC_SYNC_HOURS, 24),
        followSystemBatterySaver = prefs.getBoolean(KEY_FOLLOW_SYSTEM_BATTERY_SAVER, false),
        batterySaverDisableWhoopHrStream = prefs.getBoolean(KEY_BS_DISABLE_HR_STREAM, false),
        batterySaverDisableWhoopImuStream = prefs.getBoolean(KEY_BS_DISABLE_IMU_STREAM, false),
        batterySaverDisableWhoopPpgStream = prefs.getBoolean(KEY_BS_DISABLE_PPG_STREAM, true),
        batterySaverForceGlobalWhoopInterval = prefs.getBoolean(KEY_BS_FORCE_GLOBAL_INTERVAL, true),
        batterySaverGlobalWhoopIntervalSeconds = prefs.getInt(KEY_BS_GLOBAL_INTERVAL, 5),
        enableWhoopHrStream = prefs.getBoolean(KEY_ENABLE_HR_STREAM, true),
        enableWhoopImuStream = prefs.getBoolean(KEY_ENABLE_IMU_STREAM, true),
        enableWhoopPpgStream = prefs.getBoolean(KEY_ENABLE_PPG_STREAM, true),
        globalWhoopCaptureIntervalEnabled = prefs.getBoolean(KEY_GLOBAL_ENABLED, false),
        globalWhoopCaptureIntervalSeconds = prefs.getInt(KEY_GLOBAL_INTERVAL, 1),
        imuCaptureIntervalSeconds = prefs.getInt(KEY_IMU_INTERVAL, 1),
        ppgCaptureIntervalSeconds = prefs.getInt(KEY_PPG_INTERVAL, 1),
        hrCaptureIntervalSeconds = prefs.getInt(KEY_HR_INTERVAL, 1),
        captureWhoopEvents = prefs.getBoolean(KEY_CAPTURE_EVENTS, true),
        whoopDoubleTapAction = prefs.getString(KEY_DOUBLE_TAP_ACTION, null)
            ?.let { runCatching { WhoopDoubleTapAction.valueOf(it) }.getOrNull() }
            ?: WhoopDoubleTapAction.NONE,
        enableWhoopUnknownTagPrompts = prefs.getBoolean(KEY_UNKNOWN_TAG_PROMPTS, false),
        runSleepProcessing = prefs.getBoolean(KEY_SLEEP_PROCESSING, true),
        runActivityProcessing = prefs.getBoolean(KEY_ACTIVITY_PROCESSING, true),
        importHealthConnectSleep = prefs.getBoolean(KEY_HC_SLEEP, true),
        importHealthConnectActivities = prefs.getBoolean(KEY_HC_ACTIVITIES, true),
        importHealthConnectHeartRate = prefs.getBoolean(KEY_HC_HR, true),
        healthConnectLookbackDays = prefs.getInt(KEY_HC_LOOKBACK_DAYS, 30),
    )

    private fun persist(settings: AppSettings) {
        prefs.edit()
            .putBoolean(KEY_ONBOARDING_COMPLETE, settings.hasCompletedOnboarding)
            .putString(KEY_PREFERRED_DEVICE_ADDRESS, settings.preferredWhoopDeviceAddress)
            .putString(KEY_PREFERRED_DEVICE_NAME, settings.preferredWhoopDeviceName)
            .putBoolean(KEY_ENABLE_PERIODIC_SYNC, settings.enablePeriodicHistoricalSync)
            .putInt(KEY_PERIODIC_SYNC_HOURS, settings.periodicHistoricalSyncHours)
            .putBoolean(KEY_FOLLOW_SYSTEM_BATTERY_SAVER, settings.followSystemBatterySaver)
            .putBoolean(KEY_BS_DISABLE_HR_STREAM, settings.batterySaverDisableWhoopHrStream)
            .putBoolean(KEY_BS_DISABLE_IMU_STREAM, settings.batterySaverDisableWhoopImuStream)
            .putBoolean(KEY_BS_DISABLE_PPG_STREAM, settings.batterySaverDisableWhoopPpgStream)
            .putBoolean(KEY_BS_FORCE_GLOBAL_INTERVAL, settings.batterySaverForceGlobalWhoopInterval)
            .putInt(KEY_BS_GLOBAL_INTERVAL, settings.batterySaverGlobalWhoopIntervalSeconds)
            .putBoolean(KEY_ENABLE_HR_STREAM, settings.enableWhoopHrStream)
            .putBoolean(KEY_ENABLE_IMU_STREAM, settings.enableWhoopImuStream)
            .putBoolean(KEY_ENABLE_PPG_STREAM, settings.enableWhoopPpgStream)
            .putBoolean(KEY_GLOBAL_ENABLED, settings.globalWhoopCaptureIntervalEnabled)
            .putInt(KEY_GLOBAL_INTERVAL, settings.globalWhoopCaptureIntervalSeconds)
            .putInt(KEY_IMU_INTERVAL, settings.imuCaptureIntervalSeconds)
            .putInt(KEY_PPG_INTERVAL, settings.ppgCaptureIntervalSeconds)
            .putInt(KEY_HR_INTERVAL, settings.hrCaptureIntervalSeconds)
            .putBoolean(KEY_CAPTURE_EVENTS, settings.captureWhoopEvents)
            .putString(KEY_DOUBLE_TAP_ACTION, settings.whoopDoubleTapAction.name)
            .putBoolean(KEY_UNKNOWN_TAG_PROMPTS, settings.enableWhoopUnknownTagPrompts)
            .putBoolean(KEY_SLEEP_PROCESSING, settings.runSleepProcessing)
            .putBoolean(KEY_ACTIVITY_PROCESSING, settings.runActivityProcessing)
            .putBoolean(KEY_HC_SLEEP, settings.importHealthConnectSleep)
            .putBoolean(KEY_HC_ACTIVITIES, settings.importHealthConnectActivities)
            .putBoolean(KEY_HC_HR, settings.importHealthConnectHeartRate)
            .putInt(KEY_HC_LOOKBACK_DAYS, settings.healthConnectLookbackDays)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "sploot_settings"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        private const val KEY_PREFERRED_DEVICE_ADDRESS = "preferred_device_address"
        private const val KEY_PREFERRED_DEVICE_NAME = "preferred_device_name"
        private const val KEY_ENABLE_PERIODIC_SYNC = "enable_periodic_sync"
        private const val KEY_PERIODIC_SYNC_HOURS = "periodic_sync_hours"
        private const val KEY_FOLLOW_SYSTEM_BATTERY_SAVER = "follow_system_battery_saver"
        private const val KEY_BS_DISABLE_HR_STREAM = "battery_saver_disable_hr_stream"
        private const val KEY_BS_DISABLE_IMU_STREAM = "battery_saver_disable_imu_stream"
        private const val KEY_BS_DISABLE_PPG_STREAM = "battery_saver_disable_ppg_stream"
        private const val KEY_BS_FORCE_GLOBAL_INTERVAL = "battery_saver_force_global_interval"
        private const val KEY_BS_GLOBAL_INTERVAL = "battery_saver_global_interval"
        private const val KEY_ENABLE_HR_STREAM = "enable_hr_stream"
        private const val KEY_ENABLE_IMU_STREAM = "enable_imu_stream"
        private const val KEY_ENABLE_PPG_STREAM = "enable_ppg_stream"
        private const val KEY_GLOBAL_ENABLED = "global_capture_enabled"
        private const val KEY_GLOBAL_INTERVAL = "global_capture_interval"
        private const val KEY_IMU_INTERVAL = "imu_capture_interval"
        private const val KEY_PPG_INTERVAL = "ppg_capture_interval"
        private const val KEY_HR_INTERVAL = "hr_capture_interval"
        private const val KEY_CAPTURE_EVENTS = "capture_events"
        private const val KEY_DOUBLE_TAP_ACTION = "double_tap_action"
        private const val KEY_UNKNOWN_TAG_PROMPTS = "unknown_tag_prompts"
        private const val KEY_SLEEP_PROCESSING = "sleep_processing"
        private const val KEY_ACTIVITY_PROCESSING = "activity_processing"
        private const val KEY_HC_SLEEP = "hc_sleep"
        private const val KEY_HC_ACTIVITIES = "hc_activities"
        private const val KEY_HC_HR = "hc_hr"
        private const val KEY_HC_LOOKBACK_DAYS = "hc_lookback_days"
        private const val KEY_LAST_APP_OPEN_SYNC_TRIGGER_MS = "last_app_open_sync_trigger_ms"
    }
}
