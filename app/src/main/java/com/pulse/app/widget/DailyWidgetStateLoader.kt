package com.pulse.app.widget

import com.pulse.app.health.data.cache.CachedValue
import com.pulse.app.health.data.cache.HealthDataCache
import com.pulse.app.health.data.model.HeartRateVariability
import com.pulse.app.health.data.model.HydrationSummary
import com.pulse.app.health.data.model.RestingHeartRate
import com.pulse.app.health.data.model.SleepSummary
import com.pulse.app.health.data.model.StepsSummary
import com.pulse.app.scoring.PersonalBaselineCalculator
import com.pulse.app.scoring.PulseRecoveryScoreCalculator
import com.pulse.app.scoring.PulseSleepScoreCalculator
import com.pulse.app.scoring.RecoveryScoreResult
import com.pulse.app.scoring.SleepScoreResult
import java.time.LocalDate

/**
 * Loads today's cached health data and computes the daily widget state.
 *
 * The loader does not perform any network or Google Health API calls. It reads
 * from the supplied [HealthDataCache] and runs the deterministic Pulse scoring
 * calculators locally.
 */
class DailyWidgetStateLoader(
    private val cache: HealthDataCache,
    private val stepGoal: Long = DEFAULT_STEP_GOAL,
    private val waterGoalLiters: Double = DEFAULT_WATER_GOAL_LITERS,
) {

    /**
     * Load the [DailyWidgetState] for [date].
     */
    suspend fun load(date: LocalDate = LocalDate.now()): DailyWidgetState {
        val sleep = cache.getSleep(date)?.data
        val steps = cache.getSteps(date)?.data
        val restingHeartRate = cache.getRestingHeartRate(date)?.data
        val heartRateVariability = cache.getHeartRateVariability(date)?.data
        val hydration = cache.getHydration(date)?.data

        val sleepScoreResult = sleep?.let { PulseSleepScoreCalculator.calculate(it) }
        val pulseSleepScore = (sleepScoreResult as? SleepScoreResult.Available)?.score?.totalScore

        val recoveryScoreResult = calculateRecoveryScore(
            date = date,
            sleepScoreResult = sleepScoreResult,
            heartRateVariability = heartRateVariability,
            restingHeartRate = restingHeartRate,
        )
        val pulseRecoveryScore = (recoveryScoreResult as? RecoveryScoreResult.Available)?.score?.totalScore

        return DailyWidgetState(
            date = date,
            sleepDuration = sleep?.duration?.takeIf { !it.isNegative },
            pulseSleepScore = pulseSleepScore,
            pulseRecoveryScore = pulseRecoveryScore,
            steps = steps?.steps?.takeIf { it >= 0 },
            stepsGoal = stepGoal.coerceAtLeast(1),
            stepsProgress = stepsProgress(steps, stepGoal),
            restingHeartRate = restingHeartRate?.bpm?.takeIf { it > 0 },
            waterLiters = hydration?.liters?.takeIf { it >= 0 },
            waterGoalLiters = waterGoalLiters.coerceAtLeast(0.001),
            waterProgress = waterProgress(hydration, waterGoalLiters),
        )
    }

    private suspend fun calculateRecoveryScore(
        date: LocalDate,
        sleepScoreResult: SleepScoreResult?,
        heartRateVariability: HeartRateVariability?,
        restingHeartRate: RestingHeartRate?,
    ): RecoveryScoreResult {
        val baselineWindowStart = date.minusDays(PersonalBaselineCalculator.DEFAULT_WINDOW_DAYS.toLong())
        val baselineWindowEnd = date.minusDays(1)

        val hrvHistory = cache.getHeartRateVariabilityRange(baselineWindowStart, baselineWindowEnd)
            .map(CachedValue<HeartRateVariability>::data)
        val restingHeartRateHistory = cache.getRestingHeartRateRange(baselineWindowStart, baselineWindowEnd)
            .map(CachedValue<RestingHeartRate>::data)

        val hrvBaseline = PersonalBaselineCalculator.calculateHeartRateVariabilityBaseline(
            history = hrvHistory,
            today = date,
        )
        val restingHeartRateBaseline = PersonalBaselineCalculator.calculateRestingHeartRateBaseline(
            history = restingHeartRateHistory,
            today = date,
        )

        return PulseRecoveryScoreCalculator.calculate(
            hrv = heartRateVariability,
            hrvBaseline = hrvBaseline,
            restingHeartRate = restingHeartRate,
            restingHeartRateBaseline = restingHeartRateBaseline,
            sleepScoreResult = sleepScoreResult ?: SleepScoreResult.Unavailable,
        )
    }

    private fun stepsProgress(steps: StepsSummary?, goal: Long): Float? {
        val value = steps?.steps ?: return null
        if (value < 0 || goal <= 0) return null
        return (value.toFloat() / goal).coerceIn(0f, 1f)
    }

    private fun waterProgress(hydration: HydrationSummary?, goalLiters: Double): Float? {
        val value = hydration?.liters ?: return null
        if (value < 0 || goalLiters <= 0) return null
        return (value / goalLiters).toFloat().coerceIn(0f, 1f)
    }

    companion object {
        /**
         * Default daily step goal used when a user-configurable goal is not yet
         * available. This default will be wired to settings in #15.
         */
        const val DEFAULT_STEP_GOAL: Long = 10_000L

        /**
         * Default daily water goal in liters used when a user-configurable goal
         * is not yet available. This default will be wired to settings in #15.
         */
        const val DEFAULT_WATER_GOAL_LITERS: Double = 2.5
    }
}
