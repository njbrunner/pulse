package com.pulse.app.scoring

import com.pulse.app.health.data.model.HeartRateVariability
import com.pulse.app.health.data.model.RestingHeartRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PulseRecoveryScoreCalculatorTest {

    private val date = LocalDate.of(2026, 10, 28)

    // region Representative scenarios

    @Test
    fun normalRecovery_scoresAround77() {
        val result = calculate(
            hrvMs = 55,
            hrvBaselineMs = 55.0,
            restingHeartRateBpm = 48,
            restingHeartRateBaselineBpm = 48.0,
            sleepScoreTotal = 80,
        )

        assertAvailable(result) {
            assertEquals(75, it.hrvComponent)
            assertEquals(75, it.restingHeartRateComponent)
            assertEquals(80, it.sleepScoreComponent)
            assertEquals(77, it.totalScore)
        }
    }

    @Test
    fun strongRecovery_scoresAround91() {
        val result = calculate(
            hrvMs = 65,
            hrvBaselineMs = 55.0,
            restingHeartRateBpm = 44,
            restingHeartRateBaselineBpm = 48.0,
            sleepScoreTotal = 90,
        )

        assertAvailable(result) {
            assertEquals(93, it.hrvComponent)
            assertEquals(89, it.restingHeartRateComponent)
            assertEquals(90, it.sleepScoreComponent)
            assertEquals(91, it.totalScore)
        }
    }

    @Test
    fun poorRecovery_scoresInUpper30s() {
        val result = calculate(
            hrvMs = 40,
            hrvBaselineMs = 55.0,
            restingHeartRateBpm = 55,
            restingHeartRateBaselineBpm = 48.0,
            sleepScoreTotal = 60,
        )

        assertAvailable(result) {
            assertEquals(25, it.hrvComponent)
            assertEquals(32, it.restingHeartRateComponent)
            assertEquals(60, it.sleepScoreComponent)
            assertEquals(38, it.totalScore)
        }
    }

    @Test
    fun goodSleepPoorPhysiology_scoresInUpper40s() {
        val result = calculate(
            hrvMs = 40,
            hrvBaselineMs = 55.0,
            restingHeartRateBpm = 55,
            restingHeartRateBaselineBpm = 48.0,
            sleepScoreTotal = 95,
        )

        assertAvailable(result) {
            assertEquals(25, it.hrvComponent)
            assertEquals(32, it.restingHeartRateComponent)
            assertEquals(95, it.sleepScoreComponent)
            assertEquals(48, it.totalScore)
        }
    }

    @Test
    fun poorSleepStrongPhysiology_scoresAround79() {
        val result = calculate(
            hrvMs = 65,
            hrvBaselineMs = 55.0,
            restingHeartRateBpm = 44,
            restingHeartRateBaselineBpm = 48.0,
            sleepScoreTotal = 50,
        )

        assertAvailable(result) {
            assertEquals(93, it.hrvComponent)
            assertEquals(89, it.restingHeartRateComponent)
            assertEquals(50, it.sleepScoreComponent)
            assertEquals(79, it.totalScore)
        }
    }

    // endregion

    // region HRV component curve

    @Test
    fun hrvComponentAtEachAnchor() {
        val baseline = 50.0
        assertEquals(20, hrvComponent(35, baseline))
        assertEquals(40, hrvComponent(40, baseline))
        assertEquals(60, hrvComponent(45, baseline))
        assertEquals(75, hrvComponent(50, baseline))
        assertEquals(85, hrvComponent(55, baseline))
        assertEquals(95, hrvComponent(60, baseline))
        assertEquals(100, hrvComponent(65, baseline))
    }

    @Test
    fun hrvComponentInterpolatesBetweenAnchors() {
        // Baseline 50; HRV 42 is -16% deviation, between -20% (40) and -10% (60).
        assertEquals(48, hrvComponent(42, 50.0))

        // Baseline 50; HRV 58 is +16% deviation, between +10% (85) and +20% (95).
        assertEquals(91, hrvComponent(58, 50.0))
    }

    @Test
    fun hrvComponentBelowNegative30PercentClampsTo20() {
        assertEquals(20, hrvComponent(30, 50.0))
        assertEquals(20, hrvComponent(1, 50.0))
    }

    @Test
    fun hrvComponentAbovePositive30PercentClampsTo100() {
        assertEquals(100, hrvComponent(70, 50.0))
        assertEquals(100, hrvComponent(100, 50.0))
    }

    @Test
    fun hrvComponentExactlyBaseline_returns75() {
        assertEquals(75, hrvComponent(55, 55.0))
    }

    // endregion

    // region Resting heart rate component curve

    @Test
    fun restingHeartRateComponentAtEachAnchor() {
        val baseline = 60.0
        assertEquals(100, restingHeartRateComponent(52, baseline))
        assertEquals(92, restingHeartRateComponent(55, baseline))
        assertEquals(82, restingHeartRateComponent(58, baseline))
        assertEquals(75, restingHeartRateComponent(60, baseline))
        assertEquals(65, restingHeartRateComponent(62, baseline))
        assertEquals(45, restingHeartRateComponent(65, baseline))
        assertEquals(25, restingHeartRateComponent(68, baseline))
        assertEquals(0, restingHeartRateComponent(72, baseline))
    }

    @Test
    fun restingHeartRateComponentInterpolatesBetweenAnchors() {
        // Baseline 60; RHR 63 is +3 bpm, between +2 (65) and +5 (45).
        assertEquals(58, restingHeartRateComponent(63, 60.0))

        // Baseline 60; RHR 59 is -1 bpm, between -2 (82) and 0 (75).
        assertEquals(79, restingHeartRateComponent(59, 60.0))
    }

    @Test
    fun restingHeartRateComponentAtOrBelowNegative8BpmClampsTo100() {
        assertEquals(100, restingHeartRateComponent(52, 60.0))
        assertEquals(100, restingHeartRateComponent(48, 60.0))
        assertEquals(100, restingHeartRateComponent(1, 60.0))
    }

    @Test
    fun restingHeartRateComponentAtOrAbovePositive12BpmClampsTo0() {
        assertEquals(0, restingHeartRateComponent(72, 60.0))
        assertEquals(0, restingHeartRateComponent(80, 60.0))
        assertEquals(0, restingHeartRateComponent(200, 60.0))
    }

    @Test
    fun restingHeartRateComponentExactlyBaseline_returns75() {
        assertEquals(75, restingHeartRateComponent(48, 48.0))
    }

    // endregion

    // region Availability and validation

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

    @Test
    fun nonFiniteHrvBaseline_returnsUnavailable() {
        assertUnavailable(calculateWithBaselineValues(
            hrvBaselineMs = Double.NaN,
            restingHeartRateBaselineBpm = 60.0,
        ))
        assertUnavailable(calculateWithBaselineValues(
            hrvBaselineMs = Double.POSITIVE_INFINITY,
            restingHeartRateBaselineBpm = 60.0,
        ))
        assertUnavailable(calculateWithBaselineValues(
            hrvBaselineMs = Double.NEGATIVE_INFINITY,
            restingHeartRateBaselineBpm = 60.0,
        ))
    }

    @Test
    fun nonFiniteRestingHeartRateBaseline_returnsUnavailable() {
        assertUnavailable(calculateWithBaselineValues(
            hrvBaselineMs = 50.0,
            restingHeartRateBaselineBpm = Double.NaN,
        ))
        assertUnavailable(calculateWithBaselineValues(
            hrvBaselineMs = 50.0,
            restingHeartRateBaselineBpm = Double.POSITIVE_INFINITY,
        ))
        assertUnavailable(calculateWithBaselineValues(
            hrvBaselineMs = 50.0,
            restingHeartRateBaselineBpm = Double.NEGATIVE_INFINITY,
        ))
    }

    // endregion

    // region Helpers

    private fun hrvComponent(hrvMs: Int, baselineMs: Double): Int {
        val result = calculate(
            hrvMs = hrvMs,
            hrvBaselineMs = baselineMs,
            restingHeartRateBpm = 60,
            restingHeartRateBaselineBpm = 60.0,
            sleepScoreTotal = 100,
        )
        return (result as RecoveryScoreResult.Available).score.hrvComponent
    }

    private fun restingHeartRateComponent(bpm: Int, baselineBpm: Double): Int {
        val result = calculate(
            hrvMs = 50,
            hrvBaselineMs = 50.0,
            restingHeartRateBpm = bpm,
            restingHeartRateBaselineBpm = baselineBpm,
            sleepScoreTotal = 100,
        )
        return (result as RecoveryScoreResult.Available).score.restingHeartRateComponent
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

    private fun calculateWithBaselineValues(
        hrvBaselineMs: Double,
        restingHeartRateBaselineBpm: Double,
    ): RecoveryScoreResult {
        return PulseRecoveryScoreCalculator.calculate(
            hrv = HeartRateVariability(date = date, ms = 50),
            hrvBaseline = PersonalBaseline(
                value = hrvBaselineMs,
                windowDays = 28,
                minimumHistoryDays = 14,
                valuesInWindow = 14,
            ),
            restingHeartRate = RestingHeartRate(date = date, bpm = 60),
            restingHeartRateBaseline = PersonalBaseline(
                value = restingHeartRateBaselineBpm,
                windowDays = 28,
                minimumHistoryDays = 14,
                valuesInWindow = 14,
            ),
            sleepScoreResult = SleepScoreResult.Available(
                PulseSleepScore(100, 100, 100, 100)
            ),
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

    // endregion
}
