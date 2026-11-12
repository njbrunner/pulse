package com.pulse.app.health.data

import android.content.Context
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.aggregate.AggregationResultGroupedByPeriod
import com.pulse.app.health.data.model.StepsSummary
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HealthConnectDataRepositoryStepsTest {

    /**
     * Health Connect's internal key for [StepsRecord.COUNT_TOTAL]. Tests must
     * construct [AggregationResult] maps directly because [AggregateMetric] does
     * not expose a public, non-restricted way to obtain the metric key.
     */
    private val stepsCountTotalKey = "Steps_count_total"

    private val context: Context = mockk(relaxed = true)
    private val cache = FakeHealthDataCache()
    private val client = FakeHealthConnectClient()
    private val repository = HealthConnectDataRepository(
        applicationContext = context,
        healthDataCache = cache,
        healthConnectClient = client,
    )

    private val today = LocalDate.of(2026, 10, 28)

    @Test
    fun readSteps_withCountTotal_returnsSuccessAndCaches() = runTest {
        client.aggregateResponse = aggregationResult(12_000L)

        val result = repository.readSteps(today)

        assertEquals(HealthDataResult.Success(StepsSummary(today, 12_000L)), result)
        assertEquals(1, cache.savedSteps.size)
        assertEquals(StepsSummary(today, 12_000L), cache.savedSteps.first().first)
    }

    @Test
    fun readSteps_withoutCountTotal_returnsUnavailableAndDoesNotCache() = runTest {
        client.aggregateResponse = emptyAggregationResult()

        val result = repository.readSteps(today)

        assertEquals(HealthDataResult.Unavailable, result)
        assertTrue(cache.savedSteps.isEmpty())
    }

    @Test
    fun readSteps_securityException_returnsPermissionDenied() = runTest {
        client.throwSecurityException = true

        val result = repository.readSteps(today)

        assertEquals(HealthDataResult.PermissionDenied, result)
        assertTrue(cache.savedSteps.isEmpty())
    }

    @Test
    fun readSteps_unexpectedException_returnsFailure() = runTest {
        val error = RuntimeException("Health Connect aggregate failed")
        client.throwException = error

        val result = repository.readSteps(today)

        assertTrue(result is HealthDataResult.Failure)
        assertEquals(error, (result as HealthDataResult.Failure).error)
        assertTrue(cache.savedSteps.isEmpty())
    }

    @Test
    fun readStepsRange_withBuckets_returnsSuccessAndCachesRange() = runTest {
        val day1 = today.minusDays(1)
        val day2 = today
        client.aggregateGroupByPeriodResponse = listOf(
            groupedResult(day2, 9_000L),
            groupedResult(day1, 10_000L),
        )

        val result = repository.readStepsRange(day1, day2)

        val expected = listOf(
            StepsSummary(day1, 10_000L),
            StepsSummary(day2, 9_000L),
        )
        assertEquals(HealthDataResult.Success(expected), result)
        assertEquals(1, cache.savedStepsRanges.size)
        assertEquals(expected, cache.savedStepsRanges.first().first)
    }

    @Test
    fun readStepsRange_emptyBuckets_returnsUnavailableAndDoesNotCache() = runTest {
        client.aggregateGroupByPeriodResponse = emptyList()

        val result = repository.readStepsRange(today.minusDays(1), today)

        assertEquals(HealthDataResult.Unavailable, result)
        assertTrue(cache.savedStepsRanges.isEmpty())
    }

    @Test
    fun readStepsRange_securityException_returnsPermissionDenied() = runTest {
        client.throwSecurityException = true

        val result = repository.readStepsRange(today.minusDays(1), today)

        assertEquals(HealthDataResult.PermissionDenied, result)
        assertTrue(cache.savedStepsRanges.isEmpty())
    }

    @Test
    fun readStepsRange_unexpectedException_returnsFailure() = runTest {
        val error = RuntimeException("Health Connect aggregateGroupByPeriod failed")
        client.throwException = error

        val result = repository.readStepsRange(today.minusDays(1), today)

        assertTrue(result is HealthDataResult.Failure)
        assertEquals(error, (result as HealthDataResult.Failure).error)
        assertTrue(cache.savedStepsRanges.isEmpty())
    }

    @Test
    fun readStepsRange_usesAggregateGroupByPeriodNotReadRecords() = runTest {
        // This test verifies the repository path is aggregate-based: if the fake
        // only provides an aggregateGroupByPeriod response and no StepsRecords are
        // involved, the repository still produces a successful StepsSummary.
        val day = today
        client.aggregateGroupByPeriodResponse = listOf(groupedResult(day, 5_000L))

        val result = repository.readStepsRange(day, day)

        assertEquals(
            HealthDataResult.Success(listOf(StepsSummary(day, 5_000L))),
            result,
        )
    }

    private fun aggregationResult(steps: Long): AggregationResult {
        return AggregationResult(
            longValues = mapOf(stepsCountTotalKey to steps),
            doubleValues = emptyMap(),
            dataOrigins = emptySet(),
        )
    }

    private fun emptyAggregationResult(): AggregationResult {
        return AggregationResult(
            longValues = emptyMap(),
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
}
