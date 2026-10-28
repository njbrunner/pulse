package com.pulse.app.health.data.model

import java.time.Duration
import java.time.LocalDate

/**
 * Normalized sleep data for a single day.
 *
 * Efficiency and continuity are represented as fractions in the range [0.0, 1.0]
 * when available; they are null when the underlying data is insufficient.
 */
data class SleepSummary(
    val date: LocalDate,
    val duration: Duration,
    val efficiency: Float? = null,
    val continuity: Float? = null,
)
