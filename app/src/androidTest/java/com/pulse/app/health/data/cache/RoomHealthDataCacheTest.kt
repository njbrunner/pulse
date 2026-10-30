package com.pulse.app.health.data.cache

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pulse.app.health.data.cache.local.PulseHealthDatabase
import com.pulse.app.health.data.model.HeartRateVariability
import com.pulse.app.health.data.model.HydrationSummary
import com.pulse.app.health.data.model.RestingHeartRate
import com.pulse.app.health.data.model.SleepSummary
import com.pulse.app.health.data.model.StepsSummary
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class RoomHealthDataCacheTest {

    private lateinit var database: PulseHealthDatabase
    private lateinit var cache: RoomHealthDataCache

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = PulseHealthDatabase.createInMemory(context)
        cache = RoomHealthDataCache(database = database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun saveAndGetSleep_returnsCachedValueWithTimestamp() = runTest {
        val syncedAt = Instant.parse("2026-10-28T12:00:00Z")
        val sleep = SleepSummary(
            date = LocalDate.of(2026, 10, 28),
            duration = Duration.ofHours(7).plusMinutes(30),
            efficiency = 0.85f,
            continuity = 0.78f,
        )

        cache.saveSleep(sleep, syncedAt)
        val cached = cache.getSleep(sleep.date)

        assertNotNull(cached)
        assertEquals(sleep, cached!!.data)
        assertEquals(syncedAt, cached.syncedAt)
    }

    @Test
    fun saveAndGetSleep_preservesPrimarySessionInterval() = runTest {
        val syncedAt = Instant.now()
        val zoneOffset = ZoneOffset.UTC
        val sleep = SleepSummary(
            date = LocalDate.of(2026, 10, 28),
            duration = Duration.ofHours(8),
            primarySessionStart = LocalDate.of(2026, 10, 27)
                .atTime(22, 0)
                .toInstant(zoneOffset),
            primarySessionEnd = LocalDate.of(2026, 10, 28)
                .atTime(6, 0)
                .toInstant(zoneOffset),
        )

        cache.saveSleep(sleep, syncedAt)
        val cached = cache.getSleep(sleep.date)

        assertNotNull(cached)
        assertEquals(sleep.primarySessionStart, cached!!.data.primarySessionStart)
        assertEquals(sleep.primarySessionEnd, cached.data.primarySessionEnd)
    }

    @Test
    fun saveSleepRange_andGetSleepRange_returnsOrderedValues() = runTest {
        val syncedAt = Instant.now()
        val day1 = LocalDate.of(2026, 10, 27)
        val day2 = LocalDate.of(2026, 10, 28)
        val summaries = listOf(
            SleepSummary(day1, Duration.ofHours(6)),
            SleepSummary(day2, Duration.ofHours(8)),
        )

        cache.saveSleepRange(summaries, syncedAt)
        val result = cache.getSleepRange(day1, day2)

        assertEquals(2, result.size)
        assertEquals(day1, result[0].data.date)
        assertEquals(day2, result[1].data.date)
    }

    @Test
    fun saveSleep_overwritesPreviousValue() = runTest {
        val date = LocalDate.of(2026, 10, 28)
        cache.saveSleep(SleepSummary(date, Duration.ofHours(6)), Instant.now())

        val updated = SleepSummary(date, Duration.ofHours(7))
        val newSyncTime = Instant.parse("2026-10-28T14:00:00Z")
        cache.saveSleep(updated, newSyncTime)
        val cached = cache.getSleep(date)

        assertEquals(updated.duration, cached?.data?.duration)
        assertEquals(newSyncTime, cached?.syncedAt)
    }

    @Test
    fun getSleep_forMissingDate_returnsNull() = runTest {
        val cached = cache.getSleep(LocalDate.of(2026, 10, 28))

        assertNull(cached)
    }

    @Test
    fun saveSteps_withZeroValue_isDistinctFromMissing() = runTest {
        val date = LocalDate.of(2026, 10, 28)
        val syncedAt = Instant.now()

        cache.saveSteps(StepsSummary(date, 0L), syncedAt)
        val cached = cache.getSteps(date)

        assertNotNull(cached)
        assertEquals(0L, cached!!.data.steps)
    }

    @Test
    fun saveAndGetRestingHeartRate() = runTest {
        val syncedAt = Instant.now()
        val heartRate = RestingHeartRate(
            date = LocalDate.of(2026, 10, 28),
            bpm = 58,
        )

        cache.saveRestingHeartRate(heartRate, syncedAt)
        val cached = cache.getRestingHeartRate(heartRate.date)

        assertEquals(heartRate, cached?.data)
    }

    @Test
    fun saveAndGetHeartRateVariability() = runTest {
        val syncedAt = Instant.now()
        val hrv = HeartRateVariability(
            date = LocalDate.of(2026, 10, 28),
            ms = 52,
        )

        cache.saveHeartRateVariability(hrv, syncedAt)
        val cached = cache.getHeartRateVariability(hrv.date)

        assertEquals(hrv, cached?.data)
    }

    @Test
    fun saveAndGetHydration() = runTest {
        val syncedAt = Instant.now()
        val hydration = HydrationSummary(
            date = LocalDate.of(2026, 10, 28),
            liters = 2.1,
        )

        cache.saveHydration(hydration, syncedAt)
        val cached = cache.getHydration(hydration.date)

        assertEquals(hydration, cached?.data)
    }

    @Test
    fun clear_removesAllData() = runTest {
        val date = LocalDate.of(2026, 10, 28)
        cache.saveSteps(StepsSummary(date, 10_000L), Instant.now())
        cache.saveSleep(SleepSummary(date, Duration.ofHours(7)), Instant.now())

        cache.clear()

        assertNull(cache.getSteps(date))
        assertNull(cache.getSleep(date))
    }

    @Test
    fun getStepsRange_withGap_returnsOnlyAvailableDays() = runTest {
        val syncedAt = Instant.now()
        val day1 = LocalDate.of(2026, 10, 26)
        val day3 = LocalDate.of(2026, 10, 28)
        cache.saveSteps(StepsSummary(day1, 5_000L), syncedAt)
        cache.saveSteps(StepsSummary(day3, 8_000L), syncedAt)

        val result = cache.getStepsRange(day1, day3)

        assertEquals(2, result.size)
        assertTrue(result.none { it.data.date == LocalDate.of(2026, 10, 27) })
    }
}
