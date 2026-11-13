package com.pulse.app.debug

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.pulse.app.health.data.HealthConnectDataRepository
import com.pulse.app.health.data.HealthDataResult
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

/**
 * Temporary debug helper to verify the production repository's hydration path
 * on a physical device.
 *
 * This class lives in the debug source set and is intentionally not available
 * in release builds. It only exercises the real repository implementation; it
 * does not independently query Health Connect or calculate hydration.
 *
 * This verifier is tracked for removal during the later debug-cleanup pass.
 */
internal object HydrationDebugVerification {

    private const val TAG = "PulseHydrationDebug"

    fun trigger(activity: ComponentActivity, repository: HealthConnectDataRepository) {
        activity.lifecycleScope.launch {
            val today = LocalDate.now()
            val rangeStart = today.minusDays(6)

            Log.i(
                TAG,
                "Triggering PulseHydrationDebug verification: today=$today, range=$rangeStart..$today",
            )

            logToday(repository, today)
            logRange(repository, rangeStart, today)
        }
    }

    private suspend fun logToday(
        repository: HealthConnectDataRepository,
        today: LocalDate,
    ) {
        val label = "Today ($today)"
        when (val result = repository.readHydration(today)) {
            is HealthDataResult.Success ->
                Log.i(
                    TAG,
                    "$label: Success - liters=${formatLiters(result.data.liters)}",
                )

            is HealthDataResult.Unavailable ->
                Log.i(TAG, "$label: Unavailable")

            is HealthDataResult.PermissionDenied ->
                Log.i(TAG, "$label: PermissionDenied")

            is HealthDataResult.Failure ->
                Log.e(
                    TAG,
                    "$label: Failure: ${result.error.javaClass.simpleName}: ${result.error.message}",
                )
        }
    }

    private suspend fun logRange(
        repository: HealthConnectDataRepository,
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
    ) {
        val label = "Range ($rangeStart..$rangeEnd)"
        when (val result = repository.readHydrationRange(rangeStart, rangeEnd)) {
            is HealthDataResult.Success -> {
                Log.i(TAG, "$label: Success - ${result.data.size} day(s)")
                result.data.forEach { summary ->
                    Log.i(TAG, "  ${summary.date}: liters=${formatLiters(summary.liters)}")
                }
            }

            is HealthDataResult.Unavailable ->
                Log.i(TAG, "$label: Unavailable")

            is HealthDataResult.PermissionDenied ->
                Log.i(TAG, "$label: PermissionDenied")

            is HealthDataResult.Failure ->
                Log.e(
                    TAG,
                    "$label: Failure: ${result.error.javaClass.simpleName}: ${result.error.message}",
                )
        }
    }

    private fun formatLiters(liters: Double): String {
        return String.format(Locale.US, "%.10f", liters)
    }
}
