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
     * Anchor points for the HRV component curve.
     *
     * The input is the percentage deviation of today's HRV from the personal
     * baseline. The output is the HRV component score.
     *
     * | HRV deviation | Score |
     * | -30% or lower |    20 |
     * |          -20% |    40 |
     * |          -10% |    60 |
     * |            0% |    75 |
     * |          +10% |    85 |
     * |          +20% |    95 |
     * | +30% or higher |   100 |
     */
    private val HRV_ANCHORS = listOf(
        -30.0 to 20,
        -20.0 to 40,
        -10.0 to 60,
        0.0 to 75,
        10.0 to 85,
        20.0 to 95,
        30.0 to 100,
    )

    /**
     * Anchor points for the resting heart rate component curve.
     *
     * The input is the absolute BPM difference between today's RHR and the
     * personal baseline (`today - baseline`). The output is the RHR component
     * score.
     *
     * | RHR delta | Score |
     * | -8 or lower |   100 |
     * |         -5 |    92 |
     * |         -2 |    82 |
     * |          0 |    75 |
     * |         +2 |    65 |
     * |         +5 |    45 |
     * |         +8 |    25 |
     * | +12 or higher | 0 |
     */
    private val RESTING_HEART_RATE_ANCHORS = listOf(
        -8.0 to 100,
        -5.0 to 92,
        -2.0 to 82,
        0.0 to 75,
        2.0 to 65,
        5.0 to 45,
        8.0 to 25,
        12.0 to 0,
    )

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
        if (!hrvBaseline.value.isFinite() || hrvBaseline.value <= 0.0) return false
        if (restingHeartRate.bpm <= 0) return false
        if (!restingHeartRateBaseline.value.isFinite() || restingHeartRateBaseline.value <= 0.0) return false
        if (sleepScore.totalScore !in 0..100) return false
        return true
    }

    /**
     * Compute the HRV component score from [hrvMs] and [baselineMs].
     *
     * Uses percentage deviation from the personal baseline and piecewise-linear
     * interpolation over [HRV_ANCHORS].
     */
    private fun hrvComponent(hrvMs: Int, baselineMs: Double): Int {
        val deviation = ((hrvMs - baselineMs) / baselineMs) * 100.0
        return piecewiseLinear(deviation, HRV_ANCHORS)
    }

    /**
     * Compute the resting heart rate component score from [bpm] and
     * [baselineBpm].
     *
     * Uses the absolute BPM difference (`bpm - baselineBpm`) and piecewise-
     * linear interpolation over [RESTING_HEART_RATE_ANCHORS].
     */
    private fun restingHeartRateComponent(bpm: Int, baselineBpm: Double): Int {
        val delta = bpm - baselineBpm
        return piecewiseLinear(delta, RESTING_HEART_RATE_ANCHORS)
    }

    /**
     * Piecewise-linear interpolation through [anchors].
     *
     * Anchors must be sorted by input (x) in ascending order. Values outside the
     * anchor range are clamped to the nearest endpoint. The result is rounded to
     * the nearest integer and clamped to the range [0, 100].
     */
    private fun piecewiseLinear(
        x: Double,
        anchors: List<Pair<Double, Int>>,
    ): Int {
        require(anchors.isNotEmpty()) { "anchors must not be empty" }

        val first = anchors.first()
        val last = anchors.last()

        if (x <= first.first) return first.second
        if (x >= last.first) return last.second

        for (i in 0 until anchors.lastIndex) {
            val (x1, y1) = anchors[i]
            val (x2, y2) = anchors[i + 1]
            if (x in x1..x2) {
                val t = (x - x1) / (x2 - x1)
                val interpolated = y1 + t * (y2 - y1)
                return interpolated.roundToInt().coerceIn(0, 100)
            }
        }

        return first.second
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
