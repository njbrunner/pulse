package com.pulse.app.health.data.model

import java.time.LocalDate

/**
 * Normalized heart rate variability for a single day.
 */
data class HeartRateVariability(
    val date: LocalDate,
    val ms: Int,
)
