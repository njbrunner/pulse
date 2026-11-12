package com.pulse.app.health.data

import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.aggregate.AggregationResultGroupedByPeriod
import com.pulse.app.health.data.model.StepsSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StepsDataNormalizerTest {

    private val date = LocalDate.of(2026, 10, 28)

    /**
     * Health Connect's internal key for [StepsRecord.COUNT_TOTAL]. Tests must
     * construct [AggregationResult] maps directly because [AggregateMetric] does
     * not expose a public, non-restricted way to obtain the metric key.
     */
    private val stepsCountTotalKey = "Steps_count_total"

    @Test
    fun toStepsSummary_withCountTotal_mapsToSummary() {
        val aggregate = aggregationResult(steps = 10_000L)

        val result = StepsDataNormalizer.toStepsSummary(aggregate, date)

        assertEquals(StepsSummary(date = date, steps = 10_000L), result)
    }

    @Test
    fun toStepsSummary_withZeroCountTotal_mapsToZeroSummary() {
        val aggregate = aggregationResult(steps = 0L)

        val result = StepsDataNormalizer.toStepsSummary(aggregate, date)

        assertEquals(StepsSummary(date = date, steps = 0L), result)
    }

    @Test
    fun toStepsSummary_withoutCountTotal_returnsNull() {
        val aggregate = AggregationResult(
            longValues = emptyMap(),
            doubleValues = emptyMap(),
            dataOrigins = emptySet(),
        )

        val result = StepsDataNormalizer.toStepsSummary(aggregate, date)

        assertNull(result)
    }

    @Test
    fun toStepsSummaries_withMultipleDays_mapsAndSortsByDate() {
        val day1 = date
        val day2 = date.plusDays(1)
        val day3 = date.plusDays(2)

        val grouped = listOf(
            groupedResult(day = day3, steps = 5_000L),
            groupedResult(day = day1, steps = 10_000L),
            groupedResult(day = day2, steps = 7_500L),
        )

        val result = StepsDataNormalizer.toStepsSummaries(grouped)

        assertEquals(3, result.size)
        assertEquals(StepsSummary(day1, 10_000L), result[0])
        assertEquals(StepsSummary(day2, 7_500L), result[1])
        assertEquals(StepsSummary(day3, 5_000L), result[2])
    }

    @Test
    fun toStepsSummaries_withMissingBucket_omitsThatDay() {
        val day1 = date
        val day2 = date.plusDays(1)
        val day3 = date.plusDays(2)

        val grouped = listOf(
            groupedResult(day = day1, steps = 10_000L),
            groupedResultWithoutSteps(day = day2),
            groupedResult(day = day3, steps = 5_000L),
        )

        val result = StepsDataNormalizer.toStepsSummaries(grouped)

        assertEquals(2, result.size)
        assertEquals(StepsSummary(day1, 10_000L), result[0])
        assertEquals(StepsSummary(day3, 5_000L), result[1])
    }

    @Test
    fun toStepsSummaries_emptyList_returnsEmptyList() {
        val result = StepsDataNormalizer.toStepsSummaries(emptyList())

        assertTrue(result.isEmpty())
    }

    @Test
    fun toStepsSummaries_localDateTimeAttribution_usesBucketStartDate() {
        // A bucket whose LocalDateTime start does not match the list order
        // should still be attributed to the correct local calendar day.
        val expectedDate = LocalDate.of(2026, 10, 15)
        val grouped = listOf(
            groupedResult(day = expectedDate, steps = 3_000L),
        )

        val result = StepsDataNormalizer.toStepsSummaries(grouped)

        assertEquals(1, result.size)
        assertEquals(expectedDate, result[0].date)
        assertEquals(3_000L, result[0].steps)
    }

    private fun aggregationResult(steps: Long): AggregationResult {
        return AggregationResult(
            longValues = mapOf(stepsCountTotalKey to steps),
            doubleValues = emptyMap(),
            dataOrigins = emptySet(),
        )
    }

    private fun groupedResult(day: LocalDate, steps: Long): AggregationResultGroupedByPeriod {
        return AggregationResultGroupedByPeriod(
            result = aggregationResult(steps),
            startTime = day.atStartOfDay(),
            endTime = day.plusDays(1).atStartOfDay(),
        )
    }

    private fun groupedResultWithoutSteps(day: LocalDate): AggregationResultGroupedByPeriod {
        return AggregationResultGroupedByPeriod(
            result = AggregationResult(
                longValues = emptyMap(),
                doubleValues = emptyMap(),
                dataOrigins = emptySet(),
            ),
            startTime = day.atStartOfDay(),
            endTime = day.plusDays(1).atStartOfDay(),
        )
    }
}
