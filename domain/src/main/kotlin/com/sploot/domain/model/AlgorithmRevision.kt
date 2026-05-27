package com.sploot.domain.model

enum class MetricFamily {
    SLEEP,
    ACTIVITY,
    RECOVERY,
    STRAIN,
    ENERGY,
    VO2MAX,
    HEART_RATE,
}

enum class AlgorithmStatus {
    ACTIVE,
    CANDIDATE,
    RETIRED,
}

data class SleepScoreParameters(
    val targetSleepMinutes: Int = 480,
    val durationWeight: Float = 0.25f,
    val deepWeight: Float = 0.30f,
    val remWeight: Float = 0.25f,
    val disturbanceWeight: Float = 0.20f,
    val targetStagePercent: Float = 0.225f,
    val stageTolerancePercent: Float = 0.025f,
    val disturbancePenaltyPerBlock: Float = 0.05f,
)

data class SleepStageThresholds(
    val awakeMovement: Float = 0.20f,
    val awakeHrHigh: Float = 80f,
    val awakeRmssdHigh: Float = 60f,
    val deepRmssd: Float = 30f,
    val deepHr: Float = 55f,
    val deepResp: Float = 14f,
    val remMinRmssd: Float = 40f,
    val remMaxMovement: Float = 0.02f,
)

data class SleepAlgorithmConfig(
    val scoreParameters: SleepScoreParameters = SleepScoreParameters(),
    val thresholds: SleepStageThresholds = SleepStageThresholds(),
)

data class AlgorithmRevision(
    val id: Long,
    val family: MetricFamily,
    val version: Int,
    val status: AlgorithmStatus,
    val createdAtMillis: Long,
    val notes: String?,
    val sleepConfig: SleepAlgorithmConfig?,
)
