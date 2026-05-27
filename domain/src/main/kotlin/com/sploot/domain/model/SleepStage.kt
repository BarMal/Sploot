package com.sploot.domain.model

/** Sleep stage classification applied to 30-second epochs. */
enum class SleepStage { DEEP, LIGHT, REM, AWAKE }

/** Source of a sleep stage label. */
enum class SleepSource { ALGO, GARMIN }
