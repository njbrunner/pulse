package com.pulse.app.debug

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.pulse.app.health.data.HealthConnectDataRepository
import com.pulse.app.health.data.HealthDataResult
import com.pulse.app.health.data.cache.RoomHealthDataCache
import com.pulse.app.health.data.model.SleepSummary
import java.time.LocalDate

/**
 * Temporary debug helper to verify the Health Connect -> normalization -> repository
 * sleep flow on a physical device.
 *
 * This class lives in the debug source set and is intentionally not available in
 * release builds.
 */
internal object SleepDebugVerification {

    private const val TAG = "PulseSleepDebug"

    fun trigger(activity: ComponentActivity) {
        activity.lifecycleScope.launch {
            val context = activity.applicationContext
            val cache = RoomHealthDataCache(context)
            val repository = HealthConnectDataRepository(context, cache)

            // Request today's attributed sleep. For an overnight session this is the
            // calendar day containing the session midpoint, which is normally the wake
            // date (e.g. Oct 27 evening -> Oct 28 morning is attributed to Oct 28).
            val date = LocalDate.now()
            Log.i(TAG, "Triggering debug sleep read for date=$date")

            when (val result = repository.readSleep(date)) {
                is HealthDataResult.Success -> logSuccess(date, result.data)
                is HealthDataResult.Unavailable ->
                    Log.i(TAG, "Result for $date: Unavailable")

                is HealthDataResult.PermissionDenied ->
                    Log.i(TAG, "Result for $date: PermissionDenied")

                is HealthDataResult.Failure ->
                    Log.e(
                        TAG,
                        "Result for $date: Failure: ${result.error.javaClass.simpleName}: " +
                            "${result.error.message}",
                    )
            }
        }
    }

    private fun logSuccess(date: LocalDate, summary: SleepSummary) {
        val duration = summary.duration
        val hours = duration.toHours()
        val minutes = duration.minusHours(hours).toMinutes()

        Log.i(TAG, "Result for $date: Success")
        Log.i(TAG, "  requestedDate=$date")
        Log.i(TAG, "  attributedDate=${summary.date}")
        Log.i(TAG, "  startTime=N/A (not stored in current SleepSummary model)")
        Log.i(TAG, "  endTime=N/A (not stored in current SleepSummary model)")
        Log.i(
            TAG,
            "  duration=${String.format("%02d:%02d (%d seconds)", hours, minutes, duration.seconds)}",
        )
        Log.i(
            TAG,
            "  efficiency=${summary.efficiency?.let { String.format("%.3f", it) } ?: "N/A"}",
        )
        Log.i(
            TAG,
            "  continuity=${summary.continuity?.let { String.format("%.3f", it) } ?: "N/A"}",
        )
    }
}
