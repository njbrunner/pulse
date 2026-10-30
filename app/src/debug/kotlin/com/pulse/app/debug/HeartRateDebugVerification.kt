package com.pulse.app.debug

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.pulse.app.health.data.HealthConnectDataRepository
import com.pulse.app.health.data.HealthDataResult
import com.pulse.app.health.data.cache.RoomHealthDataCache
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Temporary debug helper to verify the Health Connect -> normalization -> repository
 * heart-rate flow on a physical device.
 *
 * This class lives in the debug source set and is intentionally not available in
 * release builds.
 */
internal object HeartRateDebugVerification {

    private const val TAG = "PulseHeartDebug"

    fun trigger(activity: ComponentActivity) {
        activity.lifecycleScope.launch {
            val context = activity.applicationContext
            val cache = RoomHealthDataCache(context)
            val repository = HealthConnectDataRepository(context, cache)

            val today = LocalDate.now()
            val rangeStart = today.minusDays(6)
            Log.i(
                TAG,
                "Triggering debug heart-rate verification: today=$today, range=$rangeStart..$today",
            )

            verifyRestingHeartRate(repository, today, rangeStart)
            verifyHeartRateVariability(repository, today, rangeStart)
        }
    }

    private suspend fun verifyRestingHeartRate(
        repository: HealthConnectDataRepository,
        today: LocalDate,
        rangeStart: LocalDate,
    ) {
        Log.i(TAG, "=== Resting Heart Rate ===")

        when (val result = repository.readRestingHeartRate(today)) {
            is HealthDataResult.Success ->
                Log.i(TAG, "Today ($today): Success - bpm=${result.data.bpm}")

            is HealthDataResult.Unavailable ->
                Log.i(TAG, "Today ($today): Unavailable")

            is HealthDataResult.PermissionDenied ->
                Log.i(TAG, "Today ($today): PermissionDenied")

            is HealthDataResult.Failure ->
                logFailure("Today ($today)", result.error)
        }

        when (val result = repository.readRestingHeartRateRange(rangeStart, today)) {
            is HealthDataResult.Success -> {
                Log.i(
                    TAG,
                    "Range ($rangeStart..$today): Success - ${result.data.size} day(s)",
                )
                result.data.forEach { summary ->
                    Log.i(TAG, "  ${summary.date}: bpm=${summary.bpm}")
                }
            }

            is HealthDataResult.Unavailable ->
                Log.i(TAG, "Range ($rangeStart..$today): Unavailable")

            is HealthDataResult.PermissionDenied ->
                Log.i(TAG, "Range ($rangeStart..$today): PermissionDenied")

            is HealthDataResult.Failure ->
                logFailure("Range ($rangeStart..$today)", result.error)
        }
    }

    private suspend fun verifyHeartRateVariability(
        repository: HealthConnectDataRepository,
        today: LocalDate,
        rangeStart: LocalDate,
    ) {
        Log.i(TAG, "=== Heart Rate Variability ===")

        when (val result = repository.readHeartRateVariability(today)) {
            is HealthDataResult.Success ->
                Log.i(TAG, "Today ($today): Success - ms=${result.data.ms}")

            is HealthDataResult.Unavailable ->
                Log.i(TAG, "Today ($today): Unavailable")

            is HealthDataResult.PermissionDenied ->
                Log.i(TAG, "Today ($today): PermissionDenied")

            is HealthDataResult.Failure ->
                logFailure("Today ($today)", result.error)
        }

        when (val result = repository.readHeartRateVariabilityRange(rangeStart, today)) {
            is HealthDataResult.Success -> {
                Log.i(
                    TAG,
                    "Range ($rangeStart..$today): Success - ${result.data.size} day(s)",
                )
                result.data.forEach { summary ->
                    Log.i(TAG, "  ${summary.date}: ms=${summary.ms}")
                }
            }

            is HealthDataResult.Unavailable ->
                Log.i(TAG, "Range ($rangeStart..$today): Unavailable")

            is HealthDataResult.PermissionDenied ->
                Log.i(TAG, "Range ($rangeStart..$today): PermissionDenied")

            is HealthDataResult.Failure ->
                logFailure("Range ($rangeStart..$today)", result.error)
        }
    }

    private fun logFailure(label: String, error: Throwable) {
        Log.e(
            TAG,
            "$label: Failure: ${error.javaClass.simpleName}: ${error.message}",
        )
    }
}
