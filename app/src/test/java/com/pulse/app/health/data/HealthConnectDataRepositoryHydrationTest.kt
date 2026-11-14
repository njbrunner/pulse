package com.pulse.app.health.data

import android.content.Context
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.aggregate.AggregationResultGroupedByPeriod
import androidx.health.connect.client.records.HydrationRecord
import com.pulse.app.health.data.model.HydrationSummary
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class HealthConnectDataRepositoryHydrationTest {

    /**
     * Health Connect's internal key for [HydrationRecord.VOLUME_TOTAL]. Tests must
     * construct [AggregationResult] maps directly because [AggregateMetric] does
     * not expose a public, non-restricted way to obtain the metric key.
     */
    private val hydrationVolumeTotalKey = "Hydration_volume_total"

    private val context: Context = mockk(relaxed = true)
    private val cache = FakeHealthDataCache()
    private val client = FakeHealthConnectClient()
    private val repository = HealthConnectDataRepository(
        applicationContext = context,
        healthDataCache = cache,
        healthConnectClient = client,
    )

    private val today = LocalDate.of(2026, 10, 28)
    private val zoneId = ZoneId.systemDefault()

    @Test
    fun readHydration_withVolumeTotal_returnsSuccessAndCaches() = runTest {
        client.aggregateResponse = aggregationResult(1.8)

        val result = repository.readHydration(today)

        assertEquals(HealthDataResult.Success(HydrationSummary(today, 1.8)), result)
        assertEquals(1, cache.savedHydration.size)
        assertEquals(HydrationSummary(today, 1.8), cache.savedHydration.first().first)
    }

    @Test
    fun readHydration_withoutVolumeTotal_returnsUnavailableAndDoesNotCache() = runTest {
        client.aggregateResponse = emptyAggregationResult()

        val result = repository.readHydration(today)

        assertEquals(HealthDataResult.Unavailable, result)
        assertTrue(cache.savedHydration.isEmpty())
    }

    @Test
    fun readHydration_securityException_returnsPermissionDenied() = runTest {
        client.throwSecurityException = true

        val result = repository.readHydration(today)

        assertEquals(HealthDataResult.PermissionDenied, result)
        assertTrue(cache.savedHydration.isEmpty())
    }

    @Test
    fun readHydration_unexpectedException_returnsFailure() = runTest {
        val error = RuntimeException("Health Connect hydration aggregate failed")
        client.throwException = error

        val result = repository.readHydration(today)

        assertTrue(result is HealthDataResult.Failure)
        assertEquals(error, (result as HealthDataResult.Failure).error)
        assertTrue(cache.savedHydration.isEmpty())
    }

    @Test
    fun readHydration_zeroVolume_preservedAsZero() = runTest {
        client.aggregateResponse = aggregationResult(0.0)

        val result = repository.readHydration(today)

        assertEquals(HealthDataResult.Success(HydrationSummary(today, 0.0)), result)
        assertEquals(1, cache.savedHydration.size)
    }

    @Test
    fun readHydrationRange_withBuckets_returnsSuccessAndCachesRange() = runTest {
        val day1 = today.minusDays(1)
        val day2 = today
        client.aggregateGroupByPeriodResponse = listOf(
            groupedResult(day2, 1.5),
            groupedResult(day1, 2.0),
        )

        val result = repository.readHydrationRange(day1, day2)

        val expected = listOf(
            HydrationSummary(day1, 2.0),
            HydrationSummary(day2, 1.5),
        )
        assertEquals(HealthDataResult.Success(expected), result)
        assertEquals(1, cache.savedHydrationRanges.size)
        assertEquals(expected, cache.savedHydrationRanges.first().first)
    }

    @Test
    fun readHydrationRange_emptyBuckets_returnsUnavailableAndDoesNotCache() = runTest {
        client.aggregateGroupByPeriodResponse = emptyList()

        val result = repository.readHydrationRange(today.minusDays(1), today)

        assertEquals(HealthDataResult.Unavailable, result)
        assertTrue(cache.savedHydrationRanges.isEmpty())
    }

    @Test
    fun readHydrationRange_securityException_returnsPermissionDenied() = runTest {
        client.throwSecurityException = true

        val result = repository.readHydrationRange(today.minusDays(1), today)

        assertEquals(HealthDataResult.PermissionDenied, result)
        assertTrue(cache.savedHydrationRanges.isEmpty())
    }

    @Test
    fun readHydrationRange_unexpectedException_returnsFailure() = runTest {
        val error = RuntimeException("Health Connect hydration aggregateGroupByPeriod failed")
        client.throwException = error

        val result = repository.readHydrationRange(today.minusDays(1), today)

        assertTrue(result is HealthDataResult.Failure)
        assertEquals(error, (result as HealthDataResult.Failure).error)
        assertTrue(cache.savedHydrationRanges.isEmpty())
    }

    @Test
    fun readHydrationRange_resultsSortedByDate() = runTest {
        val day1 = today.minusDays(1)
        val day2 = today
        client.aggregateGroupByPeriodResponse = listOf(
            // Return out of order.
            groupedResult(day2, 1.5),
            groupedResult(day1, 2.0),
        )

        val result = repository.readHydrationRange(day1, day2)

        val expected = listOf(
            HydrationSummary(day1, 2.0),
            HydrationSummary(day2, 1.5),
        )
        assertEquals(HealthDataResult.Success(expected), result)
    }

    @Test
    fun readHydrationRange_zeroVolume_preservedAsZero() = runTest {
        val day = today
        client.aggregateGroupByPeriodResponse = listOf(groupedResult(day, 0.0))

        val result = repository.readHydrationRange(day, day)

        assertEquals(
            HealthDataResult.Success(listOf(HydrationSummary(day, 0.0))),
            result,
        )
    }

    private fun aggregationResult(liters: Double): AggregationResult {
        return AggregationResult(
            longValues = emptyMap(),
            doubleValues = mapOf(hydrationVolumeTotalKey to liters),
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

    private fun groupedResult(
        day: LocalDate,
        liters: Double,
    ): AggregationResultGroupedByPeriod {
        return AggregationResultGroupedByPeriod(
            result = aggregationResult(liters),
            startTime = day.atStartOfDay(),
            endTime = day.plusDays(1).atStartOfDay(),
        )
    }
}
