package com.pulse.app.scoring

import com.pulse.app.health.data.model.HeartRateVariability
import com.pulse.app.health.data.model.RestingHeartRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PulseRecoveryScoreCalculatorTest {

    private val date = LocalDate.of(2026, 10, 28)

    @Test
    fun allMetricsAtBaselineWithPerfectSleep_scores100() {
        val result = calculate(
            hrvMs = 50,
            hrvBaselineMs = 50.0,
            restingHeartRateBpm = 60,
            restingHeartRateBaselineBpm = 60.0,
            sleepScoreTotal = 100,
        )

        assertAvailable(result) {
            assertEquals(100, it.hrvComponent)
            assertEquals(100, it.restingHeartRateComponent)
            assertEquals(100, it.sleepScoreComponent)
            assertEquals(100, it.totalScore)
        }
    }

    @Test
    fun hrvBelowBaseline_reducesScore() {
        val result = calculate(
            hrvMs = 40,
            hrvBaselineMs = 50.0,
            restingHeartRateBpm = 60,
            restingHeartRateBaselineBpm = 60.0,
            sleepScoreTotal = 100,
        )

        assertAvailable(result) {
            // 40 / 50 = 0.8 -> 80
            assertEquals(80, it.hrvComponent)
            assertEquals(100, it.restingHeartRateComponent)
            assertEquals(100, it.sleepScoreComponent)
            // 80 * 0.40 + 100 * 0.30 + 100 * 0.30 = 32 + 30 + 30 = 92
            assertEquals(92, it.totalScore)
        }
    }

    @Test
    fun restingHeartRateAboveBaseline_reducesScore() {
        val result = calculate(
            hrvMs = 50,
            hrvBaselineMs = 50.0,
            restingHeartRateBpm = 72,
            restingHeartRateBaselineBpm = 60.0,
            sleepScoreTotal = 100,
        )

        assertAvailable(result) {
            assertEquals(100, it.hrvComponent)
            // 60 / 72 = 0.8333 -> 83
            assertEquals(83, it.restingHeartRateComponent)
            assertEquals(100, it.sleepScoreComponent)
            // 100 * 0.40 + 83 * 0.30 + 100 * 0.30 = 40 + 24.9 + 30 = 94.9 -> 95
            assertEquals(95, it.totalScore)
        }
    }

    @Test
    fun lowerRestingHeartRateContributesPositively() {
        val result = calculate(
            hrvMs = 50,
            hrvBaselineMs = 50.0,
            restingHeartRateBpm = 48,
            restingHeartRateBaselineBpm = 60.0,
            sleepScoreTotal = 100,
        )

        assertAvailable(result) {
            assertEquals(100, it.hrvComponent)
            // 60 / 48 = 1.25 -> 125 -> capped at 100
            assertEquals(100, it.restingHeartRateComponent)
            assertEquals(100, it.totalScore)
        }
    }

    @Test
    fun higherHrvContributesPositively() {
        val result = calculate(
            hrvMs = 65,
            hrvBaselineMs = 50.0,
            restingHeartRateBpm = 60,
            restingHeartRateBaselineBpm = 60.0,
            sleepScoreTotal = 100,
        )

        assertAvailable(result) {
            // 65 / 50 = 1.3 -> 130 -> capped at 100
            assertEquals(100, it.hrvComponent)
            assertEquals(100, it.restingHeartRateComponent)
            assertEquals(100, it.totalScore)
        }
    }

    @Test
    fun weightingAppliesComponentScores() {
        val result = calculate(
            hrvMs = 50,
            hrvBaselineMs = 50.0,
            restingHeartRateBpm = 60,
            restingHeartRateBaselineBpm = 60.0,
            sleepScoreTotal = 50,
        )

        assertAvailable(result) {
            // 100 * 0.40 + 100 * 0.30 + 50 * 0.30 = 40 + 30 + 15 = 85
            assertEquals(85, it.totalScore)
        }
    }

    @Test
    fun totalScoreRoundsToNearestInteger() {
        val result = calculate(
            hrvMs = 47,
            hrvBaselineMs = 50.0,
            restingHeartRateBpm = 63,
            restingHeartRateBaselineBpm = 60.0,
            sleepScoreTotal = 73,
        )

        assertAvailable(result) {
            // hrv: 47 / 50 = 0.94 -> 94
            assertEquals(94, it.hrvComponent)
            // rhr: 60 / 63 = 0.9523 -> 95
            assertEquals(95, it.restingHeartRateComponent)
            // sleep: 73
            assertEquals(73, it.sleepScoreComponent)
            // 94 * 0.40 + 95 * 0.30 + 73 * 0.30 = 37.6 + 28.5 + 21.9 = 88.0
            assertEquals(88, it.totalScore)
        }
    }

    @Test
    fun minimumInputs_produceZeroScore() {
        val result = calculate(
            hrvMs = 1,
            hrvBaselineMs = 100.0,
            restingHeartRateBpm = 300,
            restingHeartRateBaselineBpm = 1.0,
            sleepScoreTotal = 0,
        )

        assertAvailable(result) {
            assertEquals(1, it.hrvComponent)
            // 1 / 300 = 0.0033 -> 0
            assertEquals(0, it.restingHeartRateComponent)
            assertEquals(0, it.sleepScoreComponent)
            // 1 * 0.40 + 0 * 0.30 + 0 * 0.30 = 0.4 -> 0
            assertEquals(0, it.totalScore)
        }
    }

    @Test
    fun missingHrv_returnsUnavailable() {
        val result = calculate(
            hrvMs = null,
            hrvBaselineMs = 50.0,
            restingHeartRateBpm = 60,
            restingHeartRateBaselineBpm = 60.0,
            sleepScoreTotal = 100,
        )

        assertUnavailable(result)
    }

    @Test
    fun missingRestingHeartRate_returnsUnavailable() {
        val result = calculate(
            hrvMs = 50,
            hrvBaselineMs = 50.0,
            restingHeartRateBpm = null,
            restingHeartRateBaselineBpm = 60.0,
            sleepScoreTotal = 100,
        )

        assertUnavailable(result)
    }

    @Test
    fun missingSleepScore_returnsUnavailable() {
        val result = calculate(
            hrvMs = 50,
            hrvBaselineMs = 50.0,
            restingHeartRateBpm = 60,
            restingHeartRateBaselineBpm = 60.0,
            sleepScoreTotal = null,
        )

        assertUnavailable(result)
    }

    @Test
    fun missingHrvBaseline_returnsUnavailable() {
        val result = calculate(
            hrvMs = 50,
            hrvBaselineMs = null,
            restingHeartRateBpm = 60,
            restingHeartRateBaselineBpm = 60.0,
            sleepScoreTotal = 100,
        )

        assertUnavailable(result)
    }

    @Test
    fun missingRestingHeartRateBaseline_returnsUnavailable() {
        val result = calculate(
            hrvMs = 50,
            hrvBaselineMs = 50.0,
            restingHeartRateBpm = 60,
            restingHeartRateBaselineBpm = null,
            sleepScoreTotal = 100,
        )

        assertUnavailable(result)
    }

    @Test
    fun baselineWithInsufficientHistory_returnsUnavailable() {
        val baseline = PersonalBaseline(
            value = 50.0,
            windowDays = 28,
            minimumHistoryDays = 14,
            valuesInWindow = 5,
        )

        val result = PulseRecoveryScoreCalculator.calculate(
            hrv = HeartRateVariability(date = date, ms = 50),
            hrvBaseline = baseline,
            restingHeartRate = RestingHeartRate(date = date, bpm = 60),
            restingHeartRateBaseline = baseline.copy(value = 60.0),
            sleepScoreResult = SleepScoreResult.Available(
                PulseSleepScore(100, 100, 100, 100)
            ),
        )

        assertUnavailable(result)
    }

    @Test
    fun zeroHrv_returnsUnavailable() {
        val result = calculate(
            hrvMs = 0,
            hrvBaselineMs = 50.0,
            restingHeartRateBpm = 60,
            restingHeartRateBaselineBpm = 60.0,
            sleepScoreTotal = 100,
        )

        assertUnavailable(result)
    }

    @Test
    fun zeroRestingHeartRate_returnsUnavailable() {
        val result = calculate(
            hrvMs = 50,
            hrvBaselineMs = 50.0,
            restingHeartRateBpm = 0,
            restingHeartRateBaselineBpm = 60.0,
            sleepScoreTotal = 100,
        )

        assertUnavailable(result)
    }

    @Test
    fun negativeSleepScore_returnsUnavailable() {
        val result = calculate(
            hrvMs = 50,
            hrvBaselineMs = 50.0,
            restingHeartRateBpm = 60,
            restingHeartRateBaselineBpm = 60.0,
            sleepScoreTotal = -1,
        )

        assertUnavailable(result)
    }

    @Test
    fun sleepScoreAbove100_returnsUnavailable() {
        val result = calculate(
            hrvMs = 50,
            hrvBaselineMs = 50.0,
            restingHeartRateBpm = 60,
            restingHeartRateBaselineBpm = 60.0,
            sleepScoreTotal = 101,
        )

        assertUnavailable(result)
    }

    private fun calculate(
        hrvMs: Int?,
        hrvBaselineMs: Double?,
        restingHeartRateBpm: Int?,
        restingHeartRateBaselineBpm: Double?,
        sleepScoreTotal: Int?,
    ): RecoveryScoreResult {
        return PulseRecoveryScoreCalculator.calculate(
            hrv = hrvMs?.let { HeartRateVariability(date = date, ms = it) },
            hrvBaseline = hrvBaselineMs?.let {
                PersonalBaseline(
                    value = it,
                    windowDays = 28,
                    minimumHistoryDays = 14,
                    valuesInWindow = 14,
                )
            },
            restingHeartRate = restingHeartRateBpm?.let {
                RestingHeartRate(date = date, bpm = it)
            },
            restingHeartRateBaseline = restingHeartRateBaselineBpm?.let {
                PersonalBaseline(
                    value = it,
                    windowDays = 28,
                    minimumHistoryDays = 14,
                    valuesInWindow = 14,
                )
            },
            sleepScoreResult = sleepScoreTotal?.let {
                SleepScoreResult.Available(
                    PulseSleepScore(100, 100, 100, it)
                )
            } ?: SleepScoreResult.Unavailable,
        )
    }

    private fun assertAvailable(
        result: RecoveryScoreResult,
        assertions: (PulseRecoveryScore) -> Unit,
    ) {
        assertTrue("Expected Available but was $result", result is RecoveryScoreResult.Available)
        assertions((result as RecoveryScoreResult.Available).score)
    }

    private fun assertUnavailable(result: RecoveryScoreResult) {
        assertTrue(
            "Expected Unavailable but was $result",
            result is RecoveryScoreResult.Unavailable,
        )
    }
}
