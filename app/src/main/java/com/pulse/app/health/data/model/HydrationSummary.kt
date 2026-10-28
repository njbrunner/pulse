package com.pulse.app.health.data.model

import java.time.LocalDate

/**
 * Normalized hydration intake for a single day.
 */
data class HydrationSummary(
    val date: LocalDate,
    val liters: Double,
)
