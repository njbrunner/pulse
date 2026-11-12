package com.pulse.app.debug

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Debug-only heart-rate statistics helpers used by [HeartRateDebugVerification]
 * to evaluate candidate definitions for Pulse RHR.
 *
 * These functions are deliberately isolated in the debug source set and are not
 * available in release builds.
 */
internal data class HeartRateSample(
    val time: Instant,
    val bpm: Long,
    val origin: String = "",
)

/**
 * Returns the p-th percentile of [values] using linear interpolation between
 * the closest ranks (Excel PERCENTILE.INC / NumPy default "linear" method).
 *
 * The list is sorted in ascending order. For a percentile p in [0, 100]:
 *   rank = (p / 100) * (n - 1)
 *   lower = floor(rank), upper = ceil(rank)
 *   result = sorted[lower] + (rank - lower) * (sorted[upper] - sorted[lower])
 *
 * @param values the sample values; must not be empty
 * @param percentile the percentile to compute, in the range [0.0, 100.0]
 */
internal fun percentile(values: List<Long>, percentile: Double): Double {
    require(values.isNotEmpty()) { "Cannot compute percentile of an empty list" }
    require(percentile in 0.0..100.0) { "Percentile must be in [0.0, 100.0]" }

    val sorted = values.sorted()
    val rank = percentile / 100.0 * (sorted.size - 1)
    val lowerIndex = kotlin.math.floor(rank).toInt()
    val upperIndex = kotlin.math.ceil(rank).toInt()
    val lowerValue = sorted[lowerIndex].toDouble()
    val upperValue = sorted[upperIndex].toDouble()
    return lowerValue + (rank - lowerIndex) * (upperValue - lowerValue)
}

internal data class LowestWindowResult(
    val meanBpm: Double,
    val start: Instant,
    val end: Instant,
    val sampleCount: Int,
)

/**
 * Finds the lowest qualifying mean heart rate over all rolling windows of
 * [windowDuration] inside the provided sample set.
 *
 * The window is defined as the half-open interval [start, start + windowDuration).
 * The minimum mean over all possible start times is reached when the window
 * boundary aligns with a sample timestamp, so this function evaluates candidate
 * starts at every sample time and every (sample time - windowDuration).
 *
 * A window qualifies only if it contains at least [minSamples] heart-rate
 * measurements. This prevents a nearly empty window from producing an
 * artificially low mean.
 *
 * @param samples heart-rate samples; need not be sorted
 * @param windowDuration the length of each rolling window
 * @param minSamples the minimum number of samples a window must contain to be
 *   considered; this is a coverage guard, not a physiological threshold
 */
internal fun findLowestWindowMean(
    samples: List<HeartRateSample>,
    windowDuration: Duration,
    minSamples: Int,
): LowestWindowResult? {
    require(minSamples > 0) { "minSamples must be positive" }
    if (samples.size < minSamples) return null

    val sorted = samples.sortedBy { it.time }
    val windowMillis = windowDuration.toMillis()
    if (windowMillis <= 0) return null

    val firstTimeMillis = sorted.first().time.toEpochMilli()

    val candidateStarts = buildSet {
        sorted.forEach { sample ->
            add(sample.time.toEpochMilli())
            val adjusted = sample.time.toEpochMilli() - windowMillis
            if (adjusted >= firstTimeMillis) {
                add(adjusted)
            }
        }
    }.sorted()

    var best: LowestWindowResult? = null
    var left = 0
    var right = 0
    var sum = 0.0

    for (startMillis in candidateStarts) {
        val endMillis = startMillis + windowMillis

        while (right < sorted.size && sorted[right].time.toEpochMilli() < endMillis) {
            sum += sorted[right].bpm.toDouble()
            right++
        }

        while (left < sorted.size && sorted[left].time.toEpochMilli() < startMillis) {
            sum -= sorted[left].bpm.toDouble()
            left++
        }

        val count = right - left
        if (count >= minSamples) {
            val mean = sum / count
            if (best == null || mean < best.meanBpm) {
                best = LowestWindowResult(
                    meanBpm = mean,
                    start = Instant.ofEpochMilli(startMillis),
                    end = Instant.ofEpochMilli(endMillis),
                    sampleCount = count,
                )
            }
        }
    }

    return best
}

private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

internal fun formatWindow(
    result: LowestWindowResult,
    zoneId: ZoneId,
): String {
    val start = result.start.atZone(zoneId).format(TIME_FORMATTER)
    val end = result.end.atZone(zoneId).format(TIME_FORMATTER)
    return String.format(Locale.US, "%.1f (%s-%s, n=%d)", result.meanBpm, start, end, result.sampleCount)
}
