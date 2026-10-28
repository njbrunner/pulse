package com.pulse.app.health.data.model

import java.time.Duration
import java.time.LocalDate

/**
 * Normalized sleep data for a single attributed day.
 *
 * [date] is the calendar day to which the sleep session is assigned. For a
 * typical overnight session this is the wake/end date (e.g. a session from the
 * evening of Oct 27 into the morning of Oct 28 is attributed to Oct 28).
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
