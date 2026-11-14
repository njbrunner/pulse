package com.pulse.app.scoring

import com.pulse.app.health.data.model.HeartRateVariability
import com.pulse.app.health.data.model.RestingHeartRate
import java.time.LocalDate

/**
 * Calculates rolling personal baselines for HRV and resting heart rate.
 *
 * The calculator is pure and deterministic: it has no side effects and depends
 * only on its inputs, so it can be tested with synthetic data.
 *
 * Default baseline configuration:
 * - Rolling window: 28 calendar days ending yesterday (today is excluded).
 * - Minimum history: 14 values within the window.
 *
 * The baseline value is the arithmetic mean of available values inside the
 * window. Gaps are handled naturally: missing days simply reduce the count of
 * values used. If the count is below the minimum, no baseline is returned.
 */
object PersonalBaselineCalculator {

    /**
     * Default size of the rolling baseline window in calendar days.
     */
    const val DEFAULT_WINDOW_DAYS = 28

    /**
     * Default minimum number of historical values required before a baseline
     * is considered reliable enough to use.
     */
    const val DEFAULT_MINIMUM_HISTORY_DAYS = 14

    /**
     * Calculate the HRV personal baseline as of [today].
     *
     * Returns null when fewer than [minimumHistoryDays] values fall within the
     * rolling window.
     */
    fun calculateHeartRateVariabilityBaseline(
        history: List<HeartRateVariability>,
        today: LocalDate = LocalDate.now(),
        windowDays: Int = DEFAULT_WINDOW_DAYS,
        minimumHistoryDays: Int = DEFAULT_MINIMUM_HISTORY_DAYS,
    ): PersonalBaseline? {
        return calculate(
            history = history,
            today = today,
            windowDays = windowDays,
            minimumHistoryDays = minimumHistoryDays,
            valueSelector = { it.ms },
            dateSelector = { it.date },
        )
    }

    /**
     * Calculate the resting heart rate personal baseline as of [today].
     *
     * Returns null when fewer than [minimumHistoryDays] values fall within the
     * rolling window.
     */
    fun calculateRestingHeartRateBaseline(
        history: List<RestingHeartRate>,
        today: LocalDate = LocalDate.now(),
        windowDays: Int = DEFAULT_WINDOW_DAYS,
        minimumHistoryDays: Int = DEFAULT_MINIMUM_HISTORY_DAYS,
    ): PersonalBaseline? {
        return calculate(
            history = history,
            today = today,
            windowDays = windowDays,
            minimumHistoryDays = minimumHistoryDays,
            valueSelector = { it.bpm },
            dateSelector = { it.date },
        )
    }

    private fun <T> calculate(
        history: List<T>,
        today: LocalDate,
        windowDays: Int,
        minimumHistoryDays: Int,
        valueSelector: (T) -> Int,
        dateSelector: (T) -> LocalDate,
    ): PersonalBaseline? {
        require(windowDays > 0) { "windowDays must be positive" }
        require(minimumHistoryDays > 0) { "minimumHistoryDays must be positive" }

        val windowStart = today.minusDays(windowDays.toLong())
        val windowEnd = today.minusDays(1)

        val valuesInWindow = history
            .filter { dateSelector(it) in windowStart..windowEnd }
            .map(valueSelector)

        if (valuesInWindow.size < minimumHistoryDays) {
            return null
        }

        val mean = valuesInWindow.average()
        return PersonalBaseline(
            value = mean,
            windowDays = windowDays,
            minimumHistoryDays = minimumHistoryDays,
            valuesInWindow = valuesInWindow.size,
        )
    }
}
