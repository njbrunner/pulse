package com.pulse.app.health.data

import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.aggregate.AggregationResultGroupedByPeriod
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.units.Volume
import com.pulse.app.health.data.model.HydrationSummary
import java.time.LocalDate

/**
 * Maps Google Health Connect hydration aggregate results into application-level
 * [HydrationSummary] models.
 *
 * Health Connect handles source deduplication and unit conversion through the
 * Aggregate API, so Pulse only needs to convert volume totals into liters and
 * attribute them to calendar days. Days without a volume aggregate are omitted,
 * allowing callers to distinguish unavailable days from a recorded zero intake.
 */
internal object HydrationDataNormalizer {

    /**
     * Maps a single-day [AggregationResult] into a [HydrationSummary] for [date].
     *
     * Returns `null` when Health Connect reports no hydration aggregate for the
     * requested range, preserving the unavailable-day semantics.
     */
    fun toHydrationSummary(
        aggregateResult: AggregationResult,
        date: LocalDate,
    ): HydrationSummary? {
        val volume = aggregateResult[HydrationRecord.VOLUME_TOTAL] ?: return null
        return HydrationSummary(date = date, liters = volume.inLiters)
    }

    /**
     * Maps a list of period-grouped aggregate results into [HydrationSummary]s.
     *
     * Buckets that contain no hydration aggregate are skipped, and the returned
     * list is sorted by date.
     */
    fun toHydrationSummaries(
        groupedResults: List<AggregationResultGroupedByPeriod>,
    ): List<HydrationSummary> {
        return groupedResults.mapNotNull { grouped ->
            val volume = grouped.result[HydrationRecord.VOLUME_TOTAL] ?: return@mapNotNull null
            val date = grouped.startTime.toLocalDate()
            HydrationSummary(date = date, liters = volume.inLiters)
        }.sortedBy { it.date }
    }
}
