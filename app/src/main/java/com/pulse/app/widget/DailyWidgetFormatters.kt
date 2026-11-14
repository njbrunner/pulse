package com.pulse.app.widget

import java.time.Duration
import kotlin.math.roundToInt

/**
 * Pure formatting helpers for daily widget values.
 *
 * All functions return null for missing or invalid input so the widget can
 * render a safe placeholder instead of a misleading zero or empty string.
 */
object DailyWidgetFormatters {

    /**
     * Format a sleep duration as "7h 32m".
     */
    fun formatDuration(duration: Duration?): String? {
        if (duration == null || duration.isNegative) return null
        val hours = duration.toHours()
        val minutes = duration.minusHours(hours).toMinutes()
        return "${hours}h ${minutes}m"
    }

    /**
     * Format a score (0–100) as a plain integer string.
     */
    fun formatScore(score: Int?): String? {
        if (score == null || score !in 0..100) return null
        return score.toString()
    }

    /**
     * Format a step count as a plain integer string.
     */
    fun formatSteps(steps: Long?): String? {
        if (steps == null || steps < 0) return null
        return steps.toString()
    }

    /**
     * Format a resting heart rate as "62 bpm".
     */
    fun formatRestingHeartRate(bpm: Int?): String? {
        if (bpm == null || bpm <= 0) return null
        return "$bpm bpm"
    }

    /**
     * Format a water volume in liters as "1.5 L".
     */
    fun formatWaterLiters(liters: Double?): String? {
        if (liters == null || liters < 0) return null
        return "%.1f L".format(liters)
    }

    /**
     * Format a goal progress fraction (0.0–1.0) as an integer percentage.
     */
    fun formatProgress(progress: Float?): String? {
        if (progress == null || progress < 0) return null
        val percent = (progress.coerceIn(0f, 1f) * 100).roundToInt()
        return "$percent%"
    }
}
