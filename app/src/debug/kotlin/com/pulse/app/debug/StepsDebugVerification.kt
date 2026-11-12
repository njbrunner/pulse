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
 * steps flow on a physical device.
 *
 * This class lives in the debug source set and is intentionally not available in
 * release builds.
 */
internal object StepsDebugVerification {

    private const val TAG = "PulseStepsDebug"

    fun trigger(activity: ComponentActivity) {
        activity.lifecycleScope.launch {
            val context = activity.applicationContext
            val cache = RoomHealthDataCache(context)
            val repository = HealthConnectDataRepository(context, cache)

            val today = LocalDate.now()
            val rangeStart = today.minusDays(6)

            Log.i(TAG, "Triggering debug steps read: today=$today, range=$rangeStart..$today")

            when (val result = repository.readSteps(today)) {
                is HealthDataResult.Success ->
                    Log.i(TAG, "Today ($today): Success - steps=${result.data.steps}")

                is HealthDataResult.Unavailable ->
                    Log.i(TAG, "Today ($today): Unavailable")

                is HealthDataResult.PermissionDenied ->
                    Log.i(TAG, "Today ($today): PermissionDenied")

                is HealthDataResult.Failure ->
                    logFailure("Today ($today)", result.error)
            }

            when (val result = repository.readStepsRange(rangeStart, today)) {
                is HealthDataResult.Success -> {
                    Log.i(
                        TAG,
                        "Range ($rangeStart..$today): Success - ${result.data.size} day(s)",
                    )
                    result.data.forEach { summary ->
                        Log.i(TAG, "  ${summary.date}: steps=${summary.steps}")
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
    }

    private fun logFailure(label: String, error: Throwable) {
        Log.e(
            TAG,
            "$label: Failure: ${error.javaClass.simpleName}: ${error.message}",
        )
    }
}
