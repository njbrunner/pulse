package com.pulse.app.health.data.model

import java.time.LocalDate

/**
 * Normalized step count for a single day.
 */
data class StepsSummary(
    val date: LocalDate,
    val steps: Long,
)
