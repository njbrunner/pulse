package com.pulse.app.scoring

/**
 * A computed Pulse Sleep Score for a single day.
 *
 * The total score is an integer in the range [0, 100]. The individual component
 * scores are kept internally for testing and future tuning; they are not
 * presented to users as standalone scores.
 */
data class PulseSleepScore(
    val durationScore: Int,
    val efficiencyScore: Int,
    val continuityScore: Int,
    val totalScore: Int,
)
