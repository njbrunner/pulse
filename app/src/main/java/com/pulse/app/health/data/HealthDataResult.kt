package com.pulse.app.health.data

/**
 * Result of reading health data through [HealthDataRepository].
 *
 * Distinguishes between successful reads, missing/unavailable data,
 * permission problems, and unexpected API failures.
 */
sealed class HealthDataResult<out T> {

    /**
     * Data was successfully read and is available.
     */
    data class Success<T>(val data: T) : HealthDataResult<T>()

    /**
     * Data is not available for the requested range (e.g., no records recorded,
     * the user has not synced, or the metric is unsupported).
     */
    data object Unavailable : HealthDataResult<Nothing>()

    /**
     * The request failed because the required Health Connect permissions were
     * denied or revoked.
     */
    data object PermissionDenied : HealthDataResult<Nothing>()

    /**
     * The underlying Health Connect API returned an unexpected error.
     */
    data class Failure(val error: Throwable) : HealthDataResult<Nothing>()
}
