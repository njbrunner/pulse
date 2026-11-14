package com.pulse.app.scoring

import com.pulse.app.health.data.model.SleepSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate

class PulseSleepScoreCalculatorTest {

    private val date = LocalDate.of(2026, 10, 28)

    @Test
    fun perfectSleepAtTarget_scores100() {
        val result = calculate(
            duration = Duration.ofHours(8),
            efficiency = 1.0f,
            continuity = 1.0f,
        )

        assertAvailable(result) {
            assertEquals(100, it.durationScore)
            assertEquals(100, it.efficiencyScore)
            assertEquals(100, it.continuityScore)
            assertEquals(100, it.totalScore)
        }
    }

    @Test
    fun durationBelowTarget_isProportional() {
        val result = calculate(
            duration = Duration.ofHours(6),
            efficiency = 1.0f,
            continuity = 1.0f,
            target = Duration.ofHours(8),
        )

        assertAvailable(result) {
            // 6 / 8 = 0.75 -> 75
            assertEquals(75, it.durationScore)
            assertEquals(100, it.efficiencyScore)
            assertEquals(100, it.continuityScore)
            // 75 * 0.60 + 100 * 0.25 + 100 * 0.15 = 85
            assertEquals(85, it.totalScore)
        }
    }

    @Test
    fun durationAboveTarget_isCappedAt100() {
        val result = calculate(
            duration = Duration.ofHours(10),
            efficiency = 1.0f,
            continuity = 1.0f,
            target = Duration.ofHours(8),
        )

        assertAvailable(result) {
            assertEquals(100, it.durationScore)
            assertEquals(100, it.totalScore)
        }
    }

    @Test
    fun weightingAppliesComponentScores() {
        val result = calculate(
            duration = Duration.ofHours(4),
            efficiency = 0.8f,
            continuity = 0.2f,
            target = Duration.ofHours(8),
        )

        assertAvailable(result) {
            // 4 / 8 = 0.5 -> 50
            assertEquals(50, it.durationScore)
            assertEquals(80, it.efficiencyScore)
            assertEquals(20, it.continuityScore)
            // 50 * 0.60 + 80 * 0.25 + 20 * 0.15 = 53
            assertEquals(53, it.totalScore)
        }
    }

    @Test
    fun totalScoreRoundsToNearestInteger() {
        val result = calculate(
            duration = Duration.ofHours(7).plusMinutes(11),
            efficiency = 0.73f,
            continuity = 0.46f,
            target = Duration.ofHours(8),
        )

        assertAvailable(result) {
            // duration: 431 / 480 = 0.8979 -> 90
            assertEquals(90, it.durationScore)
            // efficiency: 0.73 -> 73
            assertEquals(73, it.efficiencyScore)
            // continuity: 0.46 -> 46
            assertEquals(46, it.continuityScore)
            // 90 * 0.60 + 73 * 0.25 + 46 * 0.15 = 54 + 18.25 + 6.9 = 79.15 -> 79
            assertEquals(79, it.totalScore)
        }
    }

    @Test
    fun minimumInputs_produceZeroScore() {
        val result = calculate(
            duration = Duration.ZERO,
            efficiency = 0.0f,
            continuity = 0.0f,
        )

        assertAvailable(result) {
            assertEquals(0, it.durationScore)
            assertEquals(0, it.efficiencyScore)
            assertEquals(0, it.continuityScore)
            assertEquals(0, it.totalScore)
        }
    }

    @Test
    fun missingEfficiency_returnsUnavailable() {
        val result = calculate(
            duration = Duration.ofHours(8),
            efficiency = null,
            continuity = 1.0f,
        )

        assertUnavailable(result)
    }

    @Test
    fun missingContinuity_returnsUnavailable() {
        val result = calculate(
            duration = Duration.ofHours(8),
            efficiency = 1.0f,
            continuity = null,
        )

        assertUnavailable(result)
    }

    @Test
    fun negativeDuration_returnsUnavailable() {
        val result = calculate(
            duration = Duration.ofMinutes(-30),
            efficiency = 1.0f,
            continuity = 1.0f,
        )

        assertUnavailable(result)
    }

    @Test
    fun efficiencyAboveOne_returnsUnavailable() {
        val result = calculate(
            duration = Duration.ofHours(8),
            efficiency = 1.01f,
            continuity = 1.0f,
        )

        assertUnavailable(result)
    }

    @Test
    fun efficiencyBelowZero_returnsUnavailable() {
        val result = calculate(
            duration = Duration.ofHours(8),
            efficiency = -0.01f,
            continuity = 1.0f,
        )

        assertUnavailable(result)
    }

    @Test
    fun continuityAboveOne_returnsUnavailable() {
        val result = calculate(
            duration = Duration.ofHours(8),
            efficiency = 1.0f,
            continuity = 1.1f,
        )

        assertUnavailable(result)
    }

    @Test
    fun continuityNotFinite_returnsUnavailable() {
        val result = calculate(
            duration = Duration.ofHours(8),
            efficiency = 1.0f,
            continuity = Float.NaN,
        )

        assertUnavailable(result)
    }

    @Test
    fun zeroTarget_returnsUnavailable() {
        val result = calculate(
            duration = Duration.ofHours(8),
            efficiency = 1.0f,
            continuity = 1.0f,
            target = Duration.ZERO,
        )

        assertUnavailable(result)
    }

    private fun calculate(
        duration: Duration,
        efficiency: Float?,
        continuity: Float?,
        target: Duration = PulseSleepScoreCalculator.DEFAULT_SLEEP_TARGET,
    ): SleepScoreResult {
        val summary = SleepSummary(
            date = date,
            duration = duration,
            efficiency = efficiency,
            continuity = continuity,
        )
        return PulseSleepScoreCalculator.calculate(summary, target)
    }

    private fun assertAvailable(
        result: SleepScoreResult,
        assertions: (PulseSleepScore) -> Unit,
    ) {
        assertTrue("Expected Available but was $result", result is SleepScoreResult.Available)
        assertions((result as SleepScoreResult.Available).score)
    }

    private fun assertUnavailable(result: SleepScoreResult) {
        assertTrue("Expected Unavailable but was $result", result is SleepScoreResult.Unavailable)
    }
}
