package com.pulse.app.health.data

import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.aggregate.AggregationResultGroupedByPeriod
import androidx.health.connect.client.records.StepsRecord
import com.pulse.app.health.data.model.StepsSummary
import java.time.LocalDate

/**
 * Maps Google Health Connect step aggregate results into application-level
 * [StepsSummary] models.
 *
 * Health Connect performs per-day aggregation and source deduplication through
 * the Aggregate API, so Pulse only needs to convert the returned buckets into
 * its own model. Days without aggregate data are omitted, allowing callers to
 * distinguish unavailable days from a recorded zero.
 */
internal object StepsDataNormalizer {

    /**
     * Maps a single-day [AggregationResult] into a [StepsSummary] for [date].
     *
     * Returns `null` when Health Connect reports no step aggregate for the
     * requested range, preserving the unavailable-day semantics.
     */
    fun toStepsSummary(
        aggregateResult: AggregationResult,
        date: LocalDate,
    ): StepsSummary? {
        val steps = aggregateResult[StepsRecord.COUNT_TOTAL] ?: return null
        return StepsSummary(date = date, steps = steps)
    }

    /**
     * Maps a list of period-grouped aggregate results into [StepsSummary]s.
     *
     * Buckets that contain no step aggregate are skipped, and the returned list
     * is sorted by date.
     */
    fun toStepsSummaries(
        groupedResults: List<AggregationResultGroupedByPeriod>,
    ): List<StepsSummary> {
        return groupedResults.mapNotNull { grouped ->
            val steps = grouped.result[StepsRecord.COUNT_TOTAL] ?: return@mapNotNull null
            val date = grouped.startTime.toLocalDate()
            StepsSummary(date = date, steps = steps)
        }.sortedBy { it.date }
    }
}
