package com.pulse.app.health.data.cache

import com.pulse.app.health.data.model.HeartRateVariability
import com.pulse.app.health.data.model.HydrationSummary
import com.pulse.app.health.data.model.RestingHeartRate
import com.pulse.app.health.data.model.SleepSummary
import com.pulse.app.health.data.model.StepsSummary
import java.time.Instant
import java.time.LocalDate

/**
 * Local, on-device cache for normalized health data.
 *
 * Implementations must not perform network or Google Health API calls.
 */
interface HealthDataCache {

    suspend fun saveSleep(data: SleepSummary, syncedAt: Instant = Instant.now())
    suspend fun saveSleepRange(data: List<SleepSummary>, syncedAt: Instant = Instant.now())
    suspend fun getSleep(date: LocalDate): CachedValue<SleepSummary>?
    suspend fun getSleepRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<CachedValue<SleepSummary>>

    suspend fun saveSteps(data: StepsSummary, syncedAt: Instant = Instant.now())
    suspend fun saveStepsRange(data: List<StepsSummary>, syncedAt: Instant = Instant.now())
    suspend fun getSteps(date: LocalDate): CachedValue<StepsSummary>?
    suspend fun getStepsRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<CachedValue<StepsSummary>>

    suspend fun saveRestingHeartRate(data: RestingHeartRate, syncedAt: Instant = Instant.now())
    suspend fun saveRestingHeartRateRange(
        data: List<RestingHeartRate>,
        syncedAt: Instant = Instant.now(),
    )
    suspend fun getRestingHeartRate(date: LocalDate): CachedValue<RestingHeartRate>?
    suspend fun getRestingHeartRateRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<CachedValue<RestingHeartRate>>

    suspend fun saveHeartRateVariability(
        data: HeartRateVariability,
        syncedAt: Instant = Instant.now(),
    )
    suspend fun saveHeartRateVariabilityRange(
        data: List<HeartRateVariability>,
        syncedAt: Instant = Instant.now(),
    )
    suspend fun getHeartRateVariability(date: LocalDate): CachedValue<HeartRateVariability>?
    suspend fun getHeartRateVariabilityRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<CachedValue<HeartRateVariability>>

    suspend fun saveHydration(data: HydrationSummary, syncedAt: Instant = Instant.now())
    suspend fun saveHydrationRange(
        data: List<HydrationSummary>,
        syncedAt: Instant = Instant.now(),
    )
    suspend fun getHydration(date: LocalDate): CachedValue<HydrationSummary>?
    suspend fun getHydrationRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<CachedValue<HydrationSummary>>

    suspend fun clear()
}
