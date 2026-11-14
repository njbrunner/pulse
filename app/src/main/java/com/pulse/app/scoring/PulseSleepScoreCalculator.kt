package com.pulse.app.scoring

import com.pulse.app.health.data.model.SleepSummary
import java.time.Duration
import kotlin.math.roundToInt

/**
 * Calculates Pulse's custom 0–100 Sleep Score.
 *
 * The score is always identified as a Pulse Sleep Score and is never presented
 * as an official Google or Fitbit score.
 *
 * Initial weighting:
 * - Sleep duration vs. target: 60%
 * - Sleep efficiency: 25%
 * - Sleep continuity: 15%
 *
 * The calculator is pure and deterministic: it has no side effects and depends
 * only on its inputs, so it can be tested with synthetic data.
 */
object PulseSleepScoreCalculator {

    /**
     * Default sleep target used when a user-configurable target is not yet
     * available. This default will be wired to settings in #15.
     */
    val DEFAULT_SLEEP_TARGET: Duration = Duration.ofHours(8)

    /**
     * Calculate the Pulse Sleep Score for [summary] using [target] as the
     * optimal sleep duration.
     *
     * Returns [SleepScoreResult.Unavailable] when any required input is missing
     * or invalid.
     */
    fun calculate(
        summary: SleepSummary,
        target: Duration = DEFAULT_SLEEP_TARGET,
    ): SleepScoreResult {
        if (!isValid(summary, target)) {
            return SleepScoreResult.Unavailable
        }

        val durationScore = durationComponent(summary.duration, target)
        val efficiencyScore = percentComponent(summary.efficiency!!)
        val continuityScore = percentComponent(summary.continuity!!)
        val totalScore = weightedTotal(durationScore, efficiencyScore, continuityScore)

        return SleepScoreResult.Available(
            PulseSleepScore(
                durationScore = durationScore,
                efficiencyScore = efficiencyScore,
                continuityScore = continuityScore,
                totalScore = totalScore,
            )
        )
    }

    private fun isValid(summary: SleepSummary, target: Duration): Boolean {
        if (target <= Duration.ZERO) return false
        if (summary.duration < Duration.ZERO) return false
        if (summary.efficiency == null || summary.continuity == null) return false
        if (summary.efficiency !in 0.0f..1.0f) return false
        if (summary.continuity !in 0.0f..1.0f) return false
        return true
    }

    private fun durationComponent(duration: Duration, target: Duration): Int {
        val ratio = duration.seconds.toDouble() / target.seconds.toDouble()
        return ratio.coerceIn(0.0, 1.0).times(100).roundToInt()
    }

    private fun percentComponent(fraction: Float): Int {
        return (fraction * 100).roundToInt().coerceIn(0, 100)
    }

    private fun weightedTotal(
        durationScore: Int,
        efficiencyScore: Int,
        continuityScore: Int,
    ): Int {
        val weighted = durationScore * 0.60 +
            efficiencyScore * 0.25 +
            continuityScore * 0.15
        return weighted.roundToInt().coerceIn(0, 100)
    }
}
