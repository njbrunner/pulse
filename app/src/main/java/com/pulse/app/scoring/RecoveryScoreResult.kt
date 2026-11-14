package com.pulse.app.scoring

/**
 * Result of calculating a Pulse Recovery Score.
 *
 * Distinguishes between a successfully computed score and inputs that are
 * missing, invalid, or insufficient to produce a reliable score.
 */
sealed class RecoveryScoreResult {

    /**
     * A Pulse Recovery Score was successfully computed.
     */
    data class Available(val score: PulseRecoveryScore) : RecoveryScoreResult()

    /**
     * The inputs were missing, invalid, or there is not enough historical data
     * to compute a reliable baseline, so a meaningful score cannot be produced.
     */
    data object Unavailable : RecoveryScoreResult()
}
