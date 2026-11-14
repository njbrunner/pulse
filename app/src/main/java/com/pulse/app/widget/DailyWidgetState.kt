package com.pulse.app.widget

import java.time.Duration
import java.time.LocalDate

/**
 * Display-ready state for the daily Pulse widget.
 *
 * Values are null when the underlying cached data is unavailable or invalid,
 * so the UI can render a safe placeholder instead of a misleading zero.
 */
data class DailyWidgetState(
    val date: LocalDate,
    val sleepDuration: Duration?,
    val pulseSleepScore: Int?,
    val pulseRecoveryScore: Int?,
    val steps: Long?,
    val stepsGoal: Long,
    val stepsProgress: Float?,
    val restingHeartRate: Int?,
    val waterLiters: Double?,
    val waterGoalLiters: Double,
    val waterProgress: Float?,
)
