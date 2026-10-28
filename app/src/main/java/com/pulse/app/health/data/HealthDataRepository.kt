package com.pulse.app.health.data

import com.pulse.app.health.data.model.HeartRateVariability
import com.pulse.app.health.data.model.HydrationSummary
import com.pulse.app.health.data.model.RestingHeartRate
import com.pulse.app.health.data.model.SleepSummary
import com.pulse.app.health.data.model.StepsSummary
import java.time.LocalDate

/**
 * Abstraction for reading normalized health data.
 *
 * Implementations encapsulate all Google Health Connect API access. Widget and
 * presentation code must depend on this interface, not on Health Connect SDK types.
 */
interface HealthDataRepository {

    suspend fun readSleep(date: LocalDate): HealthDataResult<SleepSummary>

    suspend fun readSleepRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): HealthDataResult<List<SleepSummary>>

    suspend fun readSteps(date: LocalDate): HealthDataResult<StepsSummary>

    suspend fun readStepsRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): HealthDataResult<List<StepsSummary>>

    suspend fun readRestingHeartRate(date: LocalDate): HealthDataResult<RestingHeartRate>

    suspend fun readRestingHeartRateRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): HealthDataResult<List<RestingHeartRate>>

    suspend fun readHeartRateVariability(date: LocalDate): HealthDataResult<HeartRateVariability>

    suspend fun readHeartRateVariabilityRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): HealthDataResult<List<HeartRateVariability>>

    suspend fun readHydration(date: LocalDate): HealthDataResult<HydrationSummary>

    suspend fun readHydrationRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): HealthDataResult<List<HydrationSummary>>
}
