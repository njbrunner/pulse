package com.pulse.app.scoring

import com.pulse.app.health.data.model.HeartRateVariability
import com.pulse.app.health.data.model.RestingHeartRate
import kotlin.math.roundToInt

/**
 * Calculates Pulse's custom 0–100 Recovery Score.
 *
 * The score is always identified as a Pulse Recovery Score and is never
 * presented as an official Google or Fitbit readiness/recovery score.
 *
 * Initial weighting:
 * - HRV relative to personal baseline: 40%
 * - Resting heart rate relative to personal baseline: 30%
 * - Pulse Sleep Score: 30%
 *
 * Higher HRV relative to baseline and lower resting heart rate relative to
 * baseline both contribute positively to the score.
 *
 * The calculator is pure and deterministic: it has no side effects and depends
 * only on its inputs, so it can be tested with synthetic data.
 */
object PulseRecoveryScoreCalculator {

    /**
     * Calculate the Pulse Recovery Score for today's [hrv], [restingHeartRate],
     * and [sleepScoreResult] using the supplied personal [hrvBaseline] and
     * [restingHeartRateBaseline].
     *
     * Returns [RecoveryScoreResult.Unavailable] when any required input is
     * missing, invalid, or when a baseline does not have enough history.
     */
    fun calculate(
        hrv: HeartRateVariability?,
        hrvBaseline: PersonalBaseline?,
        restingHeartRate: RestingHeartRate?,
        restingHeartRateBaseline: PersonalBaseline?,
        sleepScoreResult: SleepScoreResult,
    ): RecoveryScoreResult {
        if (hrv == null || hrvBaseline == null || !hrvBaseline.hasEnoughHistory) {
            return RecoveryScoreResult.Unavailable
        }
        if (restingHeartRate == null || restingHeartRateBaseline == null || !restingHeartRateBaseline.hasEnoughHistory) {
            return RecoveryScoreResult.Unavailable
        }

        val sleepScore = when (sleepScoreResult) {
            is SleepScoreResult.Available -> sleepScoreResult.score
            SleepScoreResult.Unavailable -> return RecoveryScoreResult.Unavailable
        }

        if (!isValid(hrv, hrvBaseline, restingHeartRate, restingHeartRateBaseline, sleepScore)) {
            return RecoveryScoreResult.Unavailable
        }

        val hrvComponent = hrvComponent(hrv.ms, hrvBaseline.value)
        val restingHeartRateComponent = restingHeartRateComponent(
            bpm = restingHeartRate.bpm,
            baselineBpm = restingHeartRateBaseline.value,
        )
        val sleepScoreComponent = sleepScore.totalScore
        val totalScore = weightedTotal(
            hrvComponent = hrvComponent,
            restingHeartRateComponent = restingHeartRateComponent,
            sleepScoreComponent = sleepScoreComponent,
        )

        return RecoveryScoreResult.Available(
            PulseRecoveryScore(
                hrvComponent = hrvComponent,
                restingHeartRateComponent = restingHeartRateComponent,
                sleepScoreComponent = sleepScoreComponent,
                totalScore = totalScore,
            )
        )
    }

    private fun isValid(
        hrv: HeartRateVariability,
        hrvBaseline: PersonalBaseline,
        restingHeartRate: RestingHeartRate,
        restingHeartRateBaseline: PersonalBaseline,
        sleepScore: PulseSleepScore,
    ): Boolean {
        if (hrv.ms <= 0) return false
        if (hrvBaseline.value <= 0.0) return false
        if (restingHeartRate.bpm <= 0) return false
        if (restingHeartRateBaseline.value <= 0.0) return false
        if (sleepScore.totalScore !in 0..100) return false
        return true
    }

    private fun hrvComponent(hrvMs: Int, baselineMs: Double): Int {
        val ratio = hrvMs.toDouble() / baselineMs
        return (ratio * 100).roundToInt().coerceIn(0, 100)
    }

    private fun restingHeartRateComponent(bpm: Int, baselineBpm: Double): Int {
        val ratio = baselineBpm / bpm.toDouble()
        return (ratio * 100).roundToInt().coerceIn(0, 100)
    }

    private fun weightedTotal(
        hrvComponent: Int,
        restingHeartRateComponent: Int,
        sleepScoreComponent: Int,
    ): Int {
        val weighted = hrvComponent * 0.40 +
            restingHeartRateComponent * 0.30 +
            sleepScoreComponent * 0.30
        return weighted.roundToInt().coerceIn(0, 100)
    }
}
