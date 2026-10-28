package com.pulse.app.health.data.cache

import android.content.Context
import com.pulse.app.health.data.cache.local.HeartRateVariabilityDao
import com.pulse.app.health.data.cache.local.HeartRateVariabilityEntity
import com.pulse.app.health.data.cache.local.HydrationDao
import com.pulse.app.health.data.cache.local.HydrationEntity
import com.pulse.app.health.data.cache.local.PulseHealthDatabase
import com.pulse.app.health.data.cache.local.RestingHeartRateDao
import com.pulse.app.health.data.cache.local.RestingHeartRateEntity
import com.pulse.app.health.data.cache.local.SleepDao
import com.pulse.app.health.data.cache.local.SleepEntity
import com.pulse.app.health.data.cache.local.StepsDao
import com.pulse.app.health.data.cache.local.StepsEntity
import com.pulse.app.health.data.model.HeartRateVariability
import com.pulse.app.health.data.model.HydrationSummary
import com.pulse.app.health.data.model.RestingHeartRate
import com.pulse.app.health.data.model.SleepSummary
import com.pulse.app.health.data.model.StepsSummary
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * [HealthDataCache] implementation backed by Room.
 */
class RoomHealthDataCache internal constructor(
    database: PulseHealthDatabase,
) : HealthDataCache {

    constructor(context: Context) : this(PulseHealthDatabase.create(context.applicationContext))

    private val db = database
    private val sleepDao: SleepDao = db.sleepDao()
    private val stepsDao: StepsDao = db.stepsDao()
    private val restingHeartRateDao: RestingHeartRateDao = db.restingHeartRateDao()
    private val heartRateVariabilityDao: HeartRateVariabilityDao = db.heartRateVariabilityDao()
    private val hydrationDao: HydrationDao = db.hydrationDao()

    override suspend fun saveSleep(data: SleepSummary, syncedAt: Instant) {
        sleepDao.upsert(data.toEntity(syncedAt))
    }

    override suspend fun saveSleepRange(data: List<SleepSummary>, syncedAt: Instant) {
        sleepDao.upsertAll(data.map { it.toEntity(syncedAt) })
    }

    override suspend fun getSleep(date: LocalDate): CachedValue<SleepSummary>? {
        return sleepDao.getByDate(date.toString())?.toCachedValue()
    }

    override suspend fun getSleepRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<CachedValue<SleepSummary>> {
        return sleepDao.getRange(startDate.toString(), endDate.toString())
            .map { it.toCachedValue() }
    }

    override suspend fun saveSteps(data: StepsSummary, syncedAt: Instant) {
        stepsDao.upsert(data.toEntity(syncedAt))
    }

    override suspend fun saveStepsRange(data: List<StepsSummary>, syncedAt: Instant) {
        stepsDao.upsertAll(data.map { it.toEntity(syncedAt) })
    }

    override suspend fun getSteps(date: LocalDate): CachedValue<StepsSummary>? {
        return stepsDao.getByDate(date.toString())?.toCachedValue()
    }

    override suspend fun getStepsRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<CachedValue<StepsSummary>> {
        return stepsDao.getRange(startDate.toString(), endDate.toString())
            .map { it.toCachedValue() }
    }

    override suspend fun saveRestingHeartRate(data: RestingHeartRate, syncedAt: Instant) {
        restingHeartRateDao.upsert(data.toEntity(syncedAt))
    }

    override suspend fun saveRestingHeartRateRange(
        data: List<RestingHeartRate>,
        syncedAt: Instant,
    ) {
        restingHeartRateDao.upsertAll(data.map { it.toEntity(syncedAt) })
    }

    override suspend fun getRestingHeartRate(date: LocalDate): CachedValue<RestingHeartRate>? {
        return restingHeartRateDao.getByDate(date.toString())?.toCachedValue()
    }

    override suspend fun getRestingHeartRateRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<CachedValue<RestingHeartRate>> {
        return restingHeartRateDao.getRange(startDate.toString(), endDate.toString())
            .map { it.toCachedValue() }
    }

    override suspend fun saveHeartRateVariability(
        data: HeartRateVariability,
        syncedAt: Instant,
    ) {
        heartRateVariabilityDao.upsert(data.toEntity(syncedAt))
    }

    override suspend fun saveHeartRateVariabilityRange(
        data: List<HeartRateVariability>,
        syncedAt: Instant,
    ) {
        heartRateVariabilityDao.upsertAll(data.map { it.toEntity(syncedAt) })
    }

    override suspend fun getHeartRateVariability(date: LocalDate): CachedValue<HeartRateVariability>? {
        return heartRateVariabilityDao.getByDate(date.toString())?.toCachedValue()
    }

    override suspend fun getHeartRateVariabilityRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<CachedValue<HeartRateVariability>> {
        return heartRateVariabilityDao.getRange(startDate.toString(), endDate.toString())
            .map { it.toCachedValue() }
    }

    override suspend fun saveHydration(data: HydrationSummary, syncedAt: Instant) {
        hydrationDao.upsert(data.toEntity(syncedAt))
    }

    override suspend fun saveHydrationRange(
        data: List<HydrationSummary>,
        syncedAt: Instant,
    ) {
        hydrationDao.upsertAll(data.map { it.toEntity(syncedAt) })
    }

    override suspend fun getHydration(date: LocalDate): CachedValue<HydrationSummary>? {
        return hydrationDao.getByDate(date.toString())?.toCachedValue()
    }

    override suspend fun getHydrationRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<CachedValue<HydrationSummary>> {
        return hydrationDao.getRange(startDate.toString(), endDate.toString())
            .map { it.toCachedValue() }
    }

    override suspend fun clear() {
        sleepDao.clear()
        stepsDao.clear()
        restingHeartRateDao.clear()
        heartRateVariabilityDao.clear()
        hydrationDao.clear()
    }

    private fun SleepSummary.toEntity(syncedAt: Instant): SleepEntity = SleepEntity(
        date = date.toString(),
        durationSeconds = duration.seconds,
        efficiency = efficiency,
        continuity = continuity,
        syncedAtMillis = syncedAt.toEpochMilli(),
    )

    private fun SleepEntity.toCachedValue(): CachedValue<SleepSummary> = CachedValue(
        data = SleepSummary(
            date = LocalDate.parse(date),
            duration = Duration.ofSeconds(durationSeconds),
            efficiency = efficiency,
            continuity = continuity,
        ),
        syncedAt = Instant.ofEpochMilli(syncedAtMillis),
    )

    private fun StepsSummary.toEntity(syncedAt: Instant): StepsEntity = StepsEntity(
        date = date.toString(),
        steps = steps,
        syncedAtMillis = syncedAt.toEpochMilli(),
    )

    private fun StepsEntity.toCachedValue(): CachedValue<StepsSummary> = CachedValue(
        data = StepsSummary(
            date = LocalDate.parse(date),
            steps = steps,
        ),
        syncedAt = Instant.ofEpochMilli(syncedAtMillis),
    )

    private fun RestingHeartRate.toEntity(syncedAt: Instant): RestingHeartRateEntity =
        RestingHeartRateEntity(
            date = date.toString(),
            bpm = bpm,
            syncedAtMillis = syncedAt.toEpochMilli(),
        )

    private fun RestingHeartRateEntity.toCachedValue(): CachedValue<RestingHeartRate> = CachedValue(
        data = RestingHeartRate(
            date = LocalDate.parse(date),
            bpm = bpm,
        ),
        syncedAt = Instant.ofEpochMilli(syncedAtMillis),
    )

    private fun HeartRateVariability.toEntity(syncedAt: Instant): HeartRateVariabilityEntity =
        HeartRateVariabilityEntity(
            date = date.toString(),
            ms = ms,
            syncedAtMillis = syncedAt.toEpochMilli(),
        )

    private fun HeartRateVariabilityEntity.toCachedValue(): CachedValue<HeartRateVariability> =
        CachedValue(
            data = HeartRateVariability(
                date = LocalDate.parse(date),
                ms = ms,
            ),
            syncedAt = Instant.ofEpochMilli(syncedAtMillis),
        )

    private fun HydrationSummary.toEntity(syncedAt: Instant): HydrationEntity = HydrationEntity(
        date = date.toString(),
        liters = liters,
        syncedAtMillis = syncedAt.toEpochMilli(),
    )

    private fun HydrationEntity.toCachedValue(): CachedValue<HydrationSummary> = CachedValue(
        data = HydrationSummary(
            date = LocalDate.parse(date),
            liters = liters,
        ),
        syncedAt = Instant.ofEpochMilli(syncedAtMillis),
    )
}
