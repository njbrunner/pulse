package com.pulse.app.health.data.model

import java.time.LocalDate

/**
 * Normalized resting heart rate for a single day.
 */
data class RestingHeartRate(
    val date: LocalDate,
    val bpm: Int,
)
