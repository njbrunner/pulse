package com.pulse.app.scoring

/**
 * A computed Pulse Recovery Score for a single day.
 *
 * The total score is an integer in the range [0, 100]. The individual component
 * scores are kept internally for testing and future tuning; they are not
 * presented to users as standalone scores.
 *
 * The score is always identified as a Pulse Recovery Score and is never
 * presented as an official Google or Fitbit readiness/recovery score.
 */
data class PulseRecoveryScore(
    val hrvComponent: Int,
    val restingHeartRateComponent: Int,
    val sleepScoreComponent: Int,
    val totalScore: Int,
)
