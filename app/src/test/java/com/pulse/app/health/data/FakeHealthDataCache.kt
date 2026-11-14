package com.pulse.app.health.data

import com.pulse.app.health.data.cache.CachedValue
import com.pulse.app.health.data.cache.HealthDataCache
import com.pulse.app.health.data.model.HeartRateVariability
import com.pulse.app.health.data.model.HydrationSummary
import com.pulse.app.health.data.model.RestingHeartRate
import com.pulse.app.health.data.model.SleepSummary
import com.pulse.app.health.data.model.StepsSummary
import java.time.Instant
import java.time.LocalDate

/**
 * In-memory test double for [HealthDataCache] that records the data passed to
 * its save methods.
 */
class FakeHealthDataCache : HealthDataCache {

    val savedSteps = mutableListOf<Pair<StepsSummary, Instant>>()
    val savedStepsRanges = mutableListOf<Pair<List<StepsSummary>, Instant>>()

    override suspend fun saveSteps(data: StepsSummary, syncedAt: Instant) {
        savedSteps.add(data to syncedAt)
    }

    override suspend fun saveStepsRange(data: List<StepsSummary>, syncedAt: Instant) {
        savedStepsRanges.add(data to syncedAt)
    }

    override suspend fun getSteps(date: LocalDate): CachedValue<StepsSummary>? = null
    override suspend fun getStepsRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<CachedValue<StepsSummary>> = emptyList()

    override suspend fun saveSleep(data: SleepSummary, syncedAt: Instant) = Unit
    override suspend fun saveSleepRange(data: List<SleepSummary>, syncedAt: Instant) = Unit
    override suspend fun getSleep(date: LocalDate): CachedValue<SleepSummary>? = null
    override suspend fun getSleepRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<CachedValue<SleepSummary>> = emptyList()

    override suspend fun saveRestingHeartRate(data: RestingHeartRate, syncedAt: Instant) = Unit
    override suspend fun saveRestingHeartRateRange(
        data: List<RestingHeartRate>,
        syncedAt: Instant,
    ) = Unit

    override suspend fun getRestingHeartRate(date: LocalDate): CachedValue<RestingHeartRate>? = null
    override suspend fun getRestingHeartRateRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<CachedValue<RestingHeartRate>> = emptyList()

    override suspend fun saveHeartRateVariability(data: HeartRateVariability, syncedAt: Instant) = Unit
    override suspend fun saveHeartRateVariabilityRange(
        data: List<HeartRateVariability>,
        syncedAt: Instant,
    ) = Unit

    override suspend fun getHeartRateVariability(date: LocalDate): CachedValue<HeartRateVariability>? = null
    override suspend fun getHeartRateVariabilityRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<CachedValue<HeartRateVariability>> = emptyList()

    val savedHydration = mutableListOf<Pair<HydrationSummary, Instant>>()
    val savedHydrationRanges = mutableListOf<Pair<List<HydrationSummary>, Instant>>()

    override suspend fun saveHydration(data: HydrationSummary, syncedAt: Instant) {
        savedHydration.add(data to syncedAt)
    }

    override suspend fun saveHydrationRange(data: List<HydrationSummary>, syncedAt: Instant) {
        savedHydrationRanges.add(data to syncedAt)
    }

    override suspend fun getHydration(date: LocalDate): CachedValue<HydrationSummary>? = null
    override suspend fun getHydrationRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<CachedValue<HydrationSummary>> = emptyList()

    override suspend fun clear() = Unit
}
