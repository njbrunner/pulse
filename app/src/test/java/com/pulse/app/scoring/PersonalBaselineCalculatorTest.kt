package com.pulse.app.scoring

import com.pulse.app.health.data.model.HeartRateVariability
import com.pulse.app.health.data.model.RestingHeartRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PersonalBaselineCalculatorTest {

    private val today = LocalDate.of(2026, 10, 28)

    @Test
    fun enoughHistory_returnsMeanBaseline() {
        val history = (1..28).map { daysAgo ->
            HeartRateVariability(
                date = today.minusDays(daysAgo.toLong()),
                ms = 50,
            )
        }

        val baseline = PersonalBaselineCalculator.calculateHeartRateVariabilityBaseline(history, today)

        assertNotNull(baseline)
        assertEquals(50.0, baseline!!.value, 0.001)
        assertEquals(28, baseline.valuesInWindow)
        assertTrue(baseline.hasEnoughHistory)
    }

    @Test
    fun todayIsExcludedFromWindow() {
        val history = listOf(
            HeartRateVariability(date = today, ms = 999),
            HeartRateVariability(date = today.minusDays(1), ms = 50),
        )

        val baseline = PersonalBaselineCalculator.calculateHeartRateVariabilityBaseline(
            history = history,
            today = today,
            windowDays = 28,
            minimumHistoryDays = 1,
        )

        assertNotNull(baseline)
        assertEquals(50.0, baseline!!.value, 0.001)
        assertEquals(1, baseline.valuesInWindow)
    }

    @Test
    fun gapsAreHandledByUsingAvailableValues() {
        val history = listOf(
            HeartRateVariability(date = today.minusDays(2), ms = 40),
            HeartRateVariability(date = today.minusDays(5), ms = 60),
            HeartRateVariability(date = today.minusDays(10), ms = 50),
        )

        val baseline = PersonalBaselineCalculator.calculateHeartRateVariabilityBaseline(
            history = history,
            today = today,
            windowDays = 28,
            minimumHistoryDays = 3,
        )

        assertNotNull(baseline)
        assertEquals(50.0, baseline!!.value, 0.001)
        assertEquals(3, baseline.valuesInWindow)
    }

    @Test
    fun valuesOutsideWindowAreIgnored() {
        val history = listOf(
            HeartRateVariability(date = today.minusDays(1), ms = 50),
            HeartRateVariability(date = today.minusDays(29), ms = 999),
        )

        val baseline = PersonalBaselineCalculator.calculateHeartRateVariabilityBaseline(
            history = history,
            today = today,
            windowDays = 28,
            minimumHistoryDays = 1,
        )

        assertNotNull(baseline)
        assertEquals(50.0, baseline!!.value, 0.001)
        assertEquals(1, baseline.valuesInWindow)
    }

    @Test
    fun unsortedHistoryProducesSameResult() {
        val history = listOf(
            HeartRateVariability(date = today.minusDays(3), ms = 30),
            HeartRateVariability(date = today.minusDays(1), ms = 50),
            HeartRateVariability(date = today.minusDays(2), ms = 40),
        )

        val baseline = PersonalBaselineCalculator.calculateHeartRateVariabilityBaseline(
            history = history,
            today = today,
            windowDays = 28,
            minimumHistoryDays = 3,
        )

        assertNotNull(baseline)
        assertEquals(40.0, baseline!!.value, 0.001)
    }

    @Test
    fun insufficientHistory_returnsNull() {
        val history = listOf(
            HeartRateVariability(date = today.minusDays(1), ms = 50),
        )

        val baseline = PersonalBaselineCalculator.calculateHeartRateVariabilityBaseline(
            history = history,
            today = today,
            windowDays = 28,
            minimumHistoryDays = 14,
        )

        assertNull(baseline)
    }

    @Test
    fun smallerWindowAndMinimumHistoryCanSucceed() {
        val history = listOf(
            HeartRateVariability(date = today.minusDays(1), ms = 50),
            HeartRateVariability(date = today.minusDays(2), ms = 60),
        )

        val baseline = PersonalBaselineCalculator.calculateHeartRateVariabilityBaseline(
            history = history,
            today = today,
            windowDays = 7,
            minimumHistoryDays = 2,
        )

        assertNotNull(baseline)
        assertEquals(55.0, baseline!!.value, 0.001)
        assertEquals(2, baseline.valuesInWindow)
    }

    @Test
    fun restingHeartRateBaseline_calculatesMean() {
        val history = listOf(
            RestingHeartRate(date = today.minusDays(1), bpm = 60),
            RestingHeartRate(date = today.minusDays(2), bpm = 62),
            RestingHeartRate(date = today.minusDays(3), bpm = 58),
        )

        val baseline = PersonalBaselineCalculator.calculateRestingHeartRateBaseline(
            history = history,
            today = today,
            windowDays = 28,
            minimumHistoryDays = 3,
        )

        assertNotNull(baseline)
        assertEquals(60.0, baseline!!.value, 0.001)
        assertEquals(3, baseline.valuesInWindow)
    }

    @Test
    fun baselineRetainsConfiguration() {
        val history = listOf(
            HeartRateVariability(date = today.minusDays(1), ms = 50),
        )

        val baseline = PersonalBaselineCalculator.calculateHeartRateVariabilityBaseline(
            history = history,
            today = today,
            windowDays = 7,
            minimumHistoryDays = 1,
        )

        assertNotNull(baseline)
        assertEquals(7, baseline!!.windowDays)
        assertEquals(1, baseline.minimumHistoryDays)
    }
}
