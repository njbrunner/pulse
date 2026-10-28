package com.pulse.app.health.data

import com.pulse.app.health.data.model.HeartRateVariability
import com.pulse.app.health.data.model.HydrationSummary
import com.pulse.app.health.data.model.RestingHeartRate
import com.pulse.app.health.data.model.SleepSummary
import com.pulse.app.health.data.model.StepsSummary
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate

class FakeHealthDataRepositoryTest {

    private val repository = FakeHealthDataRepository()
    private val today = LocalDate.of(2026, 10, 28)

    @Test
    fun readSleep_returnsConfiguredSuccess() = runTest {
        val sleep = SleepSummary(
            date = today,
            duration = Duration.ofHours(7).plusMinutes(30),
        )
        repository.sleepResult = HealthDataResult.Success(sleep)

        val result = repository.readSleep(today)

        assertEquals(HealthDataResult.Success(sleep), result)
    }

    @Test
    fun readSleep_returnsConfiguredUnavailable() = runTest {
        repository.sleepResult = HealthDataResult.Unavailable

        val result = repository.readSleep(today)

        assertEquals(HealthDataResult.Unavailable, result)
    }

    @Test
    fun readSleep_returnsConfiguredPermissionDenied() = runTest {
        repository.sleepResult = HealthDataResult.PermissionDenied

        val result = repository.readSleep(today)

        assertEquals(HealthDataResult.PermissionDenied, result)
    }

    @Test
    fun readSleep_returnsConfiguredFailure() = runTest {
        val error = RuntimeException("Health Connect error")
        repository.sleepResult = HealthDataResult.Failure(error)

        val result = repository.readSleep(today)

        assertTrue(result is HealthDataResult.Failure)
        assertEquals(error, (result as HealthDataResult.Failure).error)
    }

    @Test
    fun readStepsRange_returnsConfiguredList() = runTest {
        val steps = listOf(
            StepsSummary(date = today.minusDays(1), steps = 8_000L),
            StepsSummary(date = today, steps = 10_000L),
        )
        repository.stepsRangeResult = HealthDataResult.Success(steps)

        val result = repository.readStepsRange(today.minusDays(1), today)

        assertEquals(HealthDataResult.Success(steps), result)
    }

    @Test
    fun readRestingHeartRate_returnsConfiguredValue() = runTest {
        val heartRate = RestingHeartRate(date = today, bpm = 58)
        repository.restingHeartRateResult = HealthDataResult.Success(heartRate)

        val result = repository.readRestingHeartRate(today)

        assertEquals(HealthDataResult.Success(heartRate), result)
    }

    @Test
    fun readHeartRateVariability_returnsConfiguredValue() = runTest {
        val hrv = HeartRateVariability(date = today, ms = 52)
        repository.heartRateVariabilityResult = HealthDataResult.Success(hrv)

        val result = repository.readHeartRateVariability(today)

        assertEquals(HealthDataResult.Success(hrv), result)
    }

    @Test
    fun readHydration_returnsConfiguredValue() = runTest {
        val hydration = HydrationSummary(date = today, liters = 1.8)
        repository.hydrationResult = HealthDataResult.Success(hydration)

        val result = repository.readHydration(today)

        assertEquals(HealthDataResult.Success(hydration), result)
    }
}
