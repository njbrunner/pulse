package com.pulse.app.health.data

import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import com.pulse.app.health.data.model.HeartRateVariability
import com.pulse.app.health.data.model.RestingHeartRate
import com.pulse.app.health.data.model.SleepSummary
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Normalizes Google Health Connect heart rate records into application-level
 * [RestingHeartRate] and [HeartRateVariability] models.
 *
 * Resting heart rate is produced two ways:
 *
 * 1. Native [RestingHeartRateRecord] values are grouped by calendar day and
 *    averaged when multiple records exist for the same day.
 * 2. When no native record is available, Pulse derives RHR from raw
 *    [HeartRateRecord] samples captured during the primary overnight sleep
 *    session. The derivation is described in [normalizeRestingHeartRate].
 *
 * [normalizeHeartRateVariability] attributes HRV to the wake date of the primary
 * overnight sleep session. It averages only the RMSSD records whose timestamps
 * fall within the primary sleep session interval.
 *
 * [preferNativeRestingHeartRate] resolves the precedence between native and
 * derived RHR values, preferring a native record when one is present.
 */
internal object HeartRateDataNormalizer {

    /**
     * Minimum inclusive BPM considered valid for a [HeartRateRecord.Sample].
     * Values outside [MIN_VALID_BPM]..[MAX_VALID_BPM] are excluded from Pulse
     * RHR calculations.
     */
    private const val MIN_VALID_BPM = 1L

    /**
     * Maximum inclusive BPM considered valid for a [HeartRateRecord.Sample].
     * Health Connect documents this range as 1-300 BPM.
     */
    private const val MAX_VALID_BPM = 300L

    /** Duration of the rolling window used to derive Pulse RHR. */
    private val WINDOW_DURATION = Duration.ofMinutes(10)

    /**
     * Minimum span that qualifying samples must cover within a [WINDOW_DURATION]
     * window. Requiring 8 minutes of span inside a 10-minute window ensures the
     * measurements are distributed across the interval rather than clustered in
     * one portion of it. This is a source-agnostic coverage rule: Fitbit provides
     * very dense samples, while other Health Connect sources may sample much less
     * frequently.
     */
    private val MIN_WINDOW_SPAN_COVERAGE = Duration.ofMinutes(8)

    /**
     * Minimum number of samples a qualifying window must contain. This guards
     * against a tiny number of observations representing the window while the
     * span requirement above guards against clustering.
     */
    private const val MIN_WINDOW_SAMPLES = 5

    /**
     * Normalizes native [RestingHeartRateRecord]s into [RestingHeartRate]s grouped
     * by calendar day. Multiple records on the same day are averaged and rounded.
     */
    fun normalizeRestingHeartRate(
        records: List<RestingHeartRateRecord>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<RestingHeartRate> {
        return records
            .groupBy { it.toLocalDate(zoneId) }
            .map { (date, dayRecords) ->
                RestingHeartRate(
                    date = date,
                    bpm = averageBpm(dayRecords),
                )
            }
            .sortedBy { it.date }
    }

    /**
     * Derives Pulse RHR from raw [HeartRateRecord] samples and the primary sleep
     * session for each wake date.
     *
     * For each [SleepSummary] that has a primary session interval, the algorithm:
     * - Selects valid HR samples that fall inside [primarySessionStart, primarySessionEnd].
     * - Evaluates every candidate 10-minute half-open window
     *   [[windowStart], [windowStart] + 10 minutes).
     * - A window qualifies only when its samples span at least
     *   [MIN_WINDOW_SPAN_COVERAGE] and contain at least [MIN_WINDOW_SAMPLES]
     *   observations.
     * - Selects the lowest qualifying mean BPM.
     * - Rounds that mean to the nearest whole BPM.
     * - Attributes the resulting [RestingHeartRate] to the sleep session's wake
     *   date ([SleepSummary.date]).
     *
     * If a sleep session has no qualifying window, no RHR is emitted for that
     * date. If there is no primary sleep session, RHR is unavailable for that
     * date.
     */
    fun normalizeRestingHeartRate(
        records: List<HeartRateRecord>,
        sleepSummaries: List<SleepSummary>,
    ): List<RestingHeartRate> {
        if (records.isEmpty() || sleepSummaries.isEmpty()) {
            return emptyList()
        }

        val allSamples = records
            .flatMap { it.samples }
            .filter { it.beatsPerMinute in MIN_VALID_BPM..MAX_VALID_BPM }
            .sortedBy { it.time }

        if (allSamples.isEmpty()) {
            return emptyList()
        }

        return sleepSummaries
            .mapNotNull { sleep -> deriveRestingHeartRateForSleep(allSamples, sleep) }
            .sortedBy { it.date }
    }

    /**
     * Resolves native vs. derived RHR precedence.
     *
     * For every date present in either [native] or [derived], a native value is
     * preferred. Derived values are used only as a fallback when no native
     * [RestingHeartRateRecord] exists for that date.
     */
    fun preferNativeRestingHeartRate(
        native: List<RestingHeartRate>,
        derived: List<RestingHeartRate>,
    ): List<RestingHeartRate> {
        val nativeByDate = native.associateBy { it.date }
        val derivedByDate = derived.associateBy { it.date }
        return (nativeByDate.keys + derivedByDate.keys)
            .sorted()
            .map { date -> nativeByDate[date] ?: derivedByDate.getValue(date) }
    }

    fun normalizeHeartRateVariability(
        records: List<HeartRateVariabilityRmssdRecord>,
        sleepSummaries: List<SleepSummary>,
    ): List<HeartRateVariability> {
        if (records.isEmpty() || sleepSummaries.isEmpty()) {
            return emptyList()
        }

        return sleepSummaries
            .mapNotNull { sleep -> normalizeHrvForSleep(records, sleep) }
            .sortedBy { it.date }
    }

    private fun deriveRestingHeartRateForSleep(
        allSamples: List<HeartRateRecord.Sample>,
        sleep: SleepSummary,
    ): RestingHeartRate? {
        val start = sleep.primarySessionStart ?: return null
        val end = sleep.primarySessionEnd ?: return null

        val sleepSamples = allSamples.filter { it.time in start..end }
        if (sleepSamples.size < MIN_WINDOW_SAMPLES) {
            return null
        }

        val lowestMean = findLowestQualifyingWindowMean(sleepSamples) ?: return null
        return RestingHeartRate(
            date = sleep.date,
            bpm = lowestMean.roundToInt(),
        )
    }

    /**
     * Finds the lowest mean BPM over all candidate 10-minute windows.
     *
     * The minimum mean over all possible window starts is reached when a window
     * boundary aligns with a sample timestamp, so candidate starts are evaluated
     * at every sample time and every (sample time - window duration).
     *
     * A window qualifies only when its samples span at least
     * [MIN_WINDOW_SPAN_COVERAGE] and contain at least [MIN_WINDOW_SAMPLES]
     * observations. This prevents a small cluster of samples from one portion of
     * the window from producing an artificially low mean.
     */
    private fun findLowestQualifyingWindowMean(
        samples: List<HeartRateRecord.Sample>,
    ): Double? {
        val sorted = samples.sortedBy { it.time }
        if (sorted.size < MIN_WINDOW_SAMPLES) return null

        val windowMillis = WINDOW_DURATION.toMillis()
        val minSpanMillis = MIN_WINDOW_SPAN_COVERAGE.toMillis()
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

        var bestMean: Double? = null
        var left = 0
        var right = 0
        var sum = 0.0

        for (startMillis in candidateStarts) {
            val endMillis = startMillis + windowMillis

            while (right < sorted.size && sorted[right].time.toEpochMilli() < endMillis) {
                sum += sorted[right].beatsPerMinute.toDouble()
                right++
            }

            while (left < sorted.size && sorted[left].time.toEpochMilli() < startMillis) {
                sum -= sorted[left].beatsPerMinute.toDouble()
                left++
            }

            val count = right - left
            if (count < MIN_WINDOW_SAMPLES) continue

            val windowFirst = sorted[left].time.toEpochMilli()
            val windowLast = sorted[right - 1].time.toEpochMilli()
            val span = windowLast - windowFirst
            if (span < minSpanMillis) continue

            val mean = sum / count
            if (bestMean == null || mean < bestMean) {
                bestMean = mean
            }
        }

        return bestMean
    }

    private fun normalizeHrvForSleep(
        records: List<HeartRateVariabilityRmssdRecord>,
        sleep: SleepSummary,
    ): HeartRateVariability? {
        val start = sleep.primarySessionStart ?: return null
        val end = sleep.primarySessionEnd ?: return null

        val sleepRecords = records.filter { it.time in start..end }
        if (sleepRecords.isEmpty()) {
            return null
        }

        return HeartRateVariability(
            date = sleep.date,
            ms = averageMs(sleepRecords),
        )
    }

    private fun averageBpm(records: List<RestingHeartRateRecord>): Int {
        val average = records.sumOf { it.beatsPerMinute }.toDouble() / records.size
        return average.roundToInt()
    }

    private fun averageMs(records: List<HeartRateVariabilityRmssdRecord>): Int {
        val average = records.sumOf { it.heartRateVariabilityMillis } / records.size
        return average.roundToInt()
    }

    private fun RestingHeartRateRecord.toLocalDate(zoneId: ZoneId): LocalDate {
        return time.atZone(zoneOffset ?: zoneId).toLocalDate()
    }
}
