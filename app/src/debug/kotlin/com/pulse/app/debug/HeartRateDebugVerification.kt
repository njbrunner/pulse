package com.pulse.app.debug

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.pulse.app.health.data.HealthConnectDataRepository
import com.pulse.app.health.data.HealthDataResult
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Temporary debug helper to verify the production repository's RHR and HRV
 * output on a physical device.
 *
 * This class lives in the debug source set and is intentionally not available
 * in release builds. It does not calculate RHR itself; it calls the production
 * repository implementation and logs only summary results.
 *
 * This verifier is tracked for removal during the later debug-cleanup pass.
 */
internal object HeartRateDebugVerification {

    private const val TAG = "PulseHeartDebug"

    fun trigger(activity: ComponentActivity, repository: HealthConnectDataRepository) {
        activity.lifecycleScope.launch {
            val today = LocalDate.now()
            val rangeStart = today.minusDays(6)

            Log.i(TAG, "Triggering PulseHeartDebug verification: today=$today, range=$rangeStart..$today")

            logRestingHeartRate(repository, today, rangeStart)
            logHeartRateVariability(repository, today, rangeStart)
        }
    }

    private suspend fun logRestingHeartRate(
        repository: HealthConnectDataRepository,
        today: LocalDate,
        rangeStart: LocalDate,
    ) {
        Log.i(TAG, "=== Repository Resting Heart Rate ===")

        when (val result = repository.readRestingHeartRate(today)) {
            is HealthDataResult.Success ->
                Log.i(TAG, "Today ($today): Success - bpm=${result.data.bpm}")

            is HealthDataResult.Unavailable ->
                Log.i(TAG, "Today ($today): Unavailable")

            is HealthDataResult.PermissionDenied ->
                Log.i(TAG, "Today ($today): PermissionDenied")

            is HealthDataResult.Failure ->
                Log.e(TAG, "Today ($today): Failure: ${result.error.javaClass.simpleName}: ${result.error.message}")
        }

        when (val result = repository.readRestingHeartRateRange(rangeStart, today)) {
            is HealthDataResult.Success -> {
                Log.i(TAG, "Range ($rangeStart..$today): Success - ${result.data.size} day(s)")
                result.data.forEach { summary ->
                    Log.i(TAG, "  ${summary.date}: bpm=${summary.bpm}")
                }
            }

            is HealthDataResult.Unavailable ->
                Log.i(TAG, "Range ($rangeStart..$today): Unavailable")

            is HealthDataResult.PermissionDenied ->
                Log.i(TAG, "Range ($rangeStart..$today): PermissionDenied")

            is HealthDataResult.Failure ->
                Log.e(TAG, "Range ($rangeStart..$today): Failure: ${result.error.javaClass.simpleName}: ${result.error.message}")
        }
    }

    private suspend fun logHeartRateVariability(
        repository: HealthConnectDataRepository,
        today: LocalDate,
        rangeStart: LocalDate,
    ) {
        Log.i(TAG, "=== Repository Heart Rate Variability ===")

        when (val result = repository.readHeartRateVariability(today)) {
            is HealthDataResult.Success ->
                Log.i(TAG, "Today ($today): Success - ms=${result.data.ms}")

            is HealthDataResult.Unavailable ->
                Log.i(TAG, "Today ($today): Unavailable")

            is HealthDataResult.PermissionDenied ->
                Log.i(TAG, "Today ($today): PermissionDenied")

            is HealthDataResult.Failure ->
                Log.e(TAG, "Today ($today): Failure: ${result.error.javaClass.simpleName}: ${result.error.message}")
        }

        when (val result = repository.readHeartRateVariabilityRange(rangeStart, today)) {
            is HealthDataResult.Success -> {
                Log.i(TAG, "Range ($rangeStart..$today): Success - ${result.data.size} day(s)")
                result.data.forEach { summary ->
                    Log.i(TAG, "  ${summary.date}: ms=${summary.ms}")
                }
            }

            is HealthDataResult.Unavailable ->
                Log.i(TAG, "Range ($rangeStart..$today): Unavailable")

            is HealthDataResult.PermissionDenied ->
                Log.i(TAG, "Range ($rangeStart..$today): PermissionDenied")

            is HealthDataResult.Failure ->
                Log.e(TAG, "Range ($rangeStart..$today): Failure: ${result.error.javaClass.simpleName}: ${result.error.message}")
        }
    }
}
