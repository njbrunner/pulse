package com.pulse.app.scoring

/**
 * A computed personal baseline for a health metric over a rolling window.
 *
 * The baseline value is the arithmetic mean of available historical values.
 * It is considered reliable only when [valuesInWindow] meets or exceeds
 * [minimumHistoryDays].
 *
 * @param value The mean value over the window.
 * @param windowDays The number of calendar days in the rolling window (today is excluded).
 * @param minimumHistoryDays The minimum number of values required for a reliable baseline.
 * @param valuesInWindow The number of non-null values that fell inside the window.
 */
data class PersonalBaseline(
    val value: Double,
    val windowDays: Int,
    val minimumHistoryDays: Int,
    val valuesInWindow: Int,
) {
    /**
     * True when there is enough historical data to produce a reliable baseline.
     */
    val hasEnoughHistory: Boolean
        get() = valuesInWindow >= minimumHistoryDays
}
