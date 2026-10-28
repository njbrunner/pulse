package com.pulse.app.health.data

import com.pulse.app.health.data.model.HeartRateVariability
import com.pulse.app.health.data.model.HydrationSummary
import com.pulse.app.health.data.model.RestingHeartRate
import com.pulse.app.health.data.model.SleepSummary
import com.pulse.app.health.data.model.StepsSummary
import java.time.LocalDate

/**
 * Test double for [HealthDataRepository].
 *
 * Each metric can be configured to return [HealthDataResult.Success],
 * [HealthDataResult.Unavailable], [HealthDataResult.PermissionDenied], or
 * [HealthDataResult.Failure] so that callers can exercise all result states
 * without touching Google Health APIs.
 */
class FakeHealthDataRepository : HealthDataRepository {

    var sleepResult: HealthDataResult<SleepSummary> = HealthDataResult.Unavailable
    var sleepRangeResult: HealthDataResult<List<SleepSummary>> = HealthDataResult.Unavailable
    var stepsResult: HealthDataResult<StepsSummary> = HealthDataResult.Unavailable
    var stepsRangeResult: HealthDataResult<List<StepsSummary>> = HealthDataResult.Unavailable
    var restingHeartRateResult: HealthDataResult<RestingHeartRate> = HealthDataResult.Unavailable
    var restingHeartRateRangeResult: HealthDataResult<List<RestingHeartRate>> = HealthDataResult.Unavailable
    var heartRateVariabilityResult: HealthDataResult<HeartRateVariability> = HealthDataResult.Unavailable
    var heartRateVariabilityRangeResult: HealthDataResult<List<HeartRateVariability>> = HealthDataResult.Unavailable
    var hydrationResult: HealthDataResult<HydrationSummary> = HealthDataResult.Unavailable
    var hydrationRangeResult: HealthDataResult<List<HydrationSummary>> = HealthDataResult.Unavailable

    override suspend fun readSleep(date: LocalDate): HealthDataResult<SleepSummary> = sleepResult

    override suspend fun readSleepRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): HealthDataResult<List<SleepSummary>> = sleepRangeResult

    override suspend fun readSteps(date: LocalDate): HealthDataResult<StepsSummary> = stepsResult

    override suspend fun readStepsRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): HealthDataResult<List<StepsSummary>> = stepsRangeResult

    override suspend fun readRestingHeartRate(date: LocalDate): HealthDataResult<RestingHeartRate> =
        restingHeartRateResult

    override suspend fun readRestingHeartRateRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): HealthDataResult<List<RestingHeartRate>> = restingHeartRateRangeResult

    override suspend fun readHeartRateVariability(
        date: LocalDate,
    ): HealthDataResult<HeartRateVariability> = heartRateVariabilityResult

    override suspend fun readHeartRateVariabilityRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): HealthDataResult<List<HeartRateVariability>> = heartRateVariabilityRangeResult

    override suspend fun readHydration(date: LocalDate): HealthDataResult<HydrationSummary> =
        hydrationResult

    override suspend fun readHydrationRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): HealthDataResult<List<HydrationSummary>> = hydrationRangeResult
}
