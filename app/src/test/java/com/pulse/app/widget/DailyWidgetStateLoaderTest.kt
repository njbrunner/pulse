package com.pulse.app.widget

import com.pulse.app.health.data.cache.CachedValue
import com.pulse.app.health.data.cache.HealthDataCache
import com.pulse.app.health.data.model.HeartRateVariability
import com.pulse.app.health.data.model.HydrationSummary
import com.pulse.app.health.data.model.RestingHeartRate
import com.pulse.app.health.data.model.SleepSummary
import com.pulse.app.health.data.model.StepsSummary
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class DailyWidgetStateLoaderTest {

    private val today = LocalDate.of(2026, 10, 28)
    private val syncedAt = Instant.parse("2026-10-28T08:00:00Z")

    private val sleep = SleepSummary(
        date = today,
        duration = Duration.ofHours(7).plusMinutes(30),
        efficiency = 0.9f,
        continuity = 0.85f,
    )
    private val steps = StepsSummary(date = today, steps = 6_432L)
    private val restingHeartRate = RestingHeartRate(date = today, bpm = 58)
    private val heartRateVariability = HeartRateVariability(date = today, ms = 55)
    private val hydration = HydrationSummary(date = today, liters = 1.5)

    @Test
    fun load_allDataAvailable_returnsCompleteState() = runTest {
        val cache = mockk<HealthDataCache>()
        val baselineHistory = baselineHistory()
        stubCache(cache, baselineHistory)

        val loader = DailyWidgetStateLoader(cache)
        val state = loader.load(today)

        assertEquals(today, state.date)
        assertEquals(sleep.duration, state.sleepDuration)
        assertNotNull(state.pulseSleepScore)
        assertNotNull(state.pulseRecoveryScore)
        assertEquals(steps.steps, state.steps)
        assertEquals(6_432L, state.stepsGoal)
        assertEquals(0.6432f, state.stepsProgress!!, 0.0001f)
        assertEquals(restingHeartRate.bpm, state.restingHeartRate)
        assertEquals(2.5, state.waterGoalLiters, 0.0001)
        assertEquals(hydration.liters, state.waterLiters!!, 0.0001)
        assertEquals(0.6f, state.waterProgress!!, 0.0001f)
    }

    @Test
    fun load_missingSleep_marksSleepAndRecoveryUnavailable() = runTest {
        val cache = mockk<HealthDataCache>()
        val baselineHistory = baselineHistory()
        stubCache(
            cache = cache,
            baselineHistory = baselineHistory,
            sleep = null,
        )

        val state = DailyWidgetStateLoader(cache).load(today)

        assertNull(state.sleepDuration)
        assertNull(state.pulseSleepScore)
        assertNull(state.pulseRecoveryScore)
        assertNotNull(state.steps)
    }

    @Test
    fun load_missingBaselineHistory_marksRecoveryUnavailable() = runTest {
        val cache = mockk<HealthDataCache>()
        stubCache(
            cache = cache,
            baselineHistory = BaselineHistory(emptyList(), emptyList()),
        )

        val state = DailyWidgetStateLoader(cache).load(today)

        assertNotNull(state.pulseSleepScore)
        assertNull(state.pulseRecoveryScore)
    }

    @Test
    fun load_missingSteps_marksStepsAndProgressUnavailable() = runTest {
        val cache = mockk<HealthDataCache>()
        val baselineHistory = baselineHistory()
        stubCache(
            cache = cache,
            baselineHistory = baselineHistory,
            steps = null,
        )

        val state = DailyWidgetStateLoader(cache).load(today)

        assertNull(state.steps)
        assertNull(state.stepsProgress)
    }

    @Test
    fun load_missingHydration_marksWaterAndProgressUnavailable() = runTest {
        val cache = mockk<HealthDataCache>()
        val baselineHistory = baselineHistory()
        stubCache(
            cache = cache,
            baselineHistory = baselineHistory,
            hydration = null,
        )

        val state = DailyWidgetStateLoader(cache).load(today)

        assertNull(state.waterLiters)
        assertNull(state.waterProgress)
    }

    @Test
    fun load_invalidSleepValues_doNotProduceScores() = runTest {
        val invalidSleep = sleep.copy(efficiency = null, continuity = null)
        val cache = mockk<HealthDataCache>()
        val baselineHistory = baselineHistory()
        stubCache(
            cache = cache,
            baselineHistory = baselineHistory,
            sleep = invalidSleep,
        )

        val state = DailyWidgetStateLoader(cache).load(today)

        assertEquals(invalidSleep.duration, state.sleepDuration)
        assertNull(state.pulseSleepScore)
        assertNull(state.pulseRecoveryScore)
    }

    @Test
    fun load_negativeCachedValues_areNotRenderedAsZeros() = runTest {
        val cache = mockk<HealthDataCache>()
        val baselineHistory = baselineHistory()
        stubCache(
            cache = cache,
            baselineHistory = baselineHistory,
            steps = StepsSummary(date = today, steps = -1L),
            restingHeartRate = RestingHeartRate(date = today, bpm = -1),
            hydration = HydrationSummary(date = today, liters = -1.0),
        )

        val state = DailyWidgetStateLoader(cache).load(today)

        assertNull(state.steps)
        assertNull(state.stepsProgress)
        assertNull(state.restingHeartRate)
        assertNull(state.waterLiters)
        assertNull(state.waterProgress)
    }

    @Test
    fun load_customGoals_areReflectedInState() = runTest {
        val cache = mockk<HealthDataCache>()
        val baselineHistory = baselineHistory()
        stubCache(cache, baselineHistory)

        val loader = DailyWidgetStateLoader(
            cache = cache,
            stepGoal = 8_000L,
            waterGoalLiters = 2.0,
        )
        val state = loader.load(today)

        assertEquals(8_000L, state.stepsGoal)
        assertEquals(0.804f, state.stepsProgress!!, 0.0001f)
        assertEquals(2.0, state.waterGoalLiters, 0.0001)
        assertEquals(0.75f, state.waterProgress!!, 0.0001f)
    }

    @Test
    fun load_progressIsCoercedToOneWhenGoalExceeded() = runTest {
        val cache = mockk<HealthDataCache>()
        val baselineHistory = baselineHistory()
        stubCache(
            cache = cache,
            baselineHistory = baselineHistory,
            steps = StepsSummary(date = today, steps = 12_000L),
            hydration = HydrationSummary(date = today, liters = 4.0),
        )

        val state = DailyWidgetStateLoader(cache).load(today)

        assertEquals(1f, state.stepsProgress!!, 0.0001f)
        assertEquals(1f, state.waterProgress!!, 0.0001f)
    }

    @Test
    fun load_goalsAreCoercedToPositiveMinimums() = runTest {
        val cache = mockk<HealthDataCache>()
        val baselineHistory = baselineHistory()
        stubCache(
            cache = cache,
            baselineHistory = baselineHistory,
            steps = StepsSummary(date = today, steps = 100L),
            hydration = HydrationSummary(date = today, liters = 0.5),
        )

        val loader = DailyWidgetStateLoader(
            cache = cache,
            stepGoal = 0L,
            waterGoalLiters = 0.0,
        )
        val state = loader.load(today)

        assertTrue(state.stepsGoal >= 1L)
        assertTrue(state.waterGoalLiters > 0)
        assertNotNull(state.stepsProgress)
        assertNotNull(state.waterProgress)
    }

    private data class BaselineHistory(
        val hrv: List<CachedValue<HeartRateVariability>>,
        val rhr: List<CachedValue<RestingHeartRate>>,
    )

    private fun baselineHistory(): BaselineHistory {
        val hrv = (1..14).map { dayOffset ->
            CachedValue(
                HeartRateVariability(
                    date = today.minusDays(dayOffset.toLong()),
                    ms = 50 + dayOffset,
                ),
                syncedAt,
            )
        }
        val rhr = (1..14).map { dayOffset ->
            CachedValue(
                RestingHeartRate(
                    date = today.minusDays(dayOffset.toLong()),
                    bpm = 60,
                ),
                syncedAt,
            )
        }
        return BaselineHistory(hrv, rhr)
    }

    private fun stubCache(
        cache: HealthDataCache,
        baselineHistory: BaselineHistory,
        sleep: SleepSummary? = this.sleep,
        steps: StepsSummary? = this.steps,
        restingHeartRate: RestingHeartRate? = this.restingHeartRate,
        heartRateVariability: HeartRateVariability? = this.heartRateVariability,
        hydration: HydrationSummary? = this.hydration,
    ) {
        coEvery { cache.getSleep(today) } returns sleep?.let { CachedValue(it, syncedAt) }
        coEvery { cache.getSteps(today) } returns steps?.let { CachedValue(it, syncedAt) }
        coEvery { cache.getRestingHeartRate(today) } returns restingHeartRate?.let {
            CachedValue(it, syncedAt)
        }
        coEvery { cache.getHeartRateVariability(today) } returns heartRateVariability?.let {
            CachedValue(it, syncedAt)
        }
        coEvery { cache.getHydration(today) } returns hydration?.let { CachedValue(it, syncedAt) }
        coEvery { cache.getHeartRateVariabilityRange(any(), any()) } returns baselineHistory.hrv
        coEvery { cache.getRestingHeartRateRange(any(), any()) } returns baselineHistory.rhr
    }
}
