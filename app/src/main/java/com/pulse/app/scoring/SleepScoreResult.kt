package com.pulse.app.scoring

/**
 * Result of calculating a Pulse Sleep Score.
 *
 * Distinguishes between a successfully computed score and inputs that are
 * missing or invalid.
 */
sealed class SleepScoreResult {

    /**
     * A Pulse Sleep Score was successfully computed.
     */
    data class Available(val score: PulseSleepScore) : SleepScoreResult()

    /**
     * The inputs were missing or invalid, so a meaningful score cannot be
     * produced.
     */
    data object Unavailable : SleepScoreResult()
}
