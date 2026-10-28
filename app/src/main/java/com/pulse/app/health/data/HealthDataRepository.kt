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

    /**
     * Reads the sleep session attributed to [date].
     *
     * The [date] is the calendar day to which the sleep session is assigned,
     * which for a normal overnight session is the wake/end date (e.g. a session
     * from the evening of Oct 27 into the morning of Oct 28 is attributed to
     * Oct 28).
     */
    suspend fun readSleep(date: LocalDate): HealthDataResult<SleepSummary>

    /**
     * Reads sleep sessions attributed to each calendar day in the inclusive
     * range [[startDate], [endDate]].
     *
     * Each returned [SleepSummary.date] is the attributed day, normally the wake
     * date of the corresponding overnight session.
     */
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
