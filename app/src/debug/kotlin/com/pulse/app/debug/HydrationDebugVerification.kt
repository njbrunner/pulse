package com.pulse.app.debug

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import com.pulse.app.health.data.HealthConnectDataRepository
import com.pulse.app.health.data.HealthDataResult
import com.pulse.app.health.data.readAllRecords
import com.pulse.app.health.permission.HealthConnectPermissionManager
import com.pulse.app.health.permission.PulseHealthPermission
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
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

    private val investigationStart = LocalDate.of(2026, 11, 1)
    private val investigationEnd = LocalDate.of(2026, 11, 14)
    private val nov3 = LocalDate.of(2026, 11, 3)
    private val nov13 = LocalDate.of(2026, 11, 13)

    fun trigger(
        activity: ComponentActivity,
        repository: HealthConnectDataRepository,
        client: HealthConnectClient?,
        permissionManager: HealthConnectPermissionManager?,
    ) {
        activity.lifecycleScope.launch {
            val today = LocalDate.now()
            val rangeStart = today.minusDays(6)

            Log.i(
                TAG,
                "Triggering PulseHydrationDebug verification: today=$today, range=$rangeStart..$today",
            )

            logSingleDate(repository, today, "Today")
            logSingleDate(repository, nov3, "Nov 3")
            logRange(repository, rangeStart, today)
            investigateRawHydration(client, permissionManager)
        }
    }

    private suspend fun logSingleDate(
        repository: HealthConnectDataRepository,
        date: LocalDate,
        name: String,
    ) {
        val label = "$name ($date)"
        when (val result = repository.readHydration(date)) {
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

    private suspend fun investigateRawHydration(
        client: HealthConnectClient?,
        permissionManager: HealthConnectPermissionManager?,
    ) {
        Log.i(TAG, "=== Raw hydration investigation ===")

        logPermissionState(permissionManager)

        if (client == null) {
            Log.i(TAG, "Health Connect client is unavailable; skipping raw query.")
            Log.i(TAG, "=== End raw hydration investigation ===")
            return
        }

        val zoneId = ZoneId.systemDefault()
        val queryStartLocal = investigationStart.atStartOfDay(zoneId)
        val queryEndLocal = investigationEnd.plusDays(1).atStartOfDay(zoneId)
        val queryStartInstant = queryStartLocal.toInstant()
        val queryEndInstant = queryEndLocal.toInstant()

        Log.i(TAG, "Raw query local boundaries: $queryStartLocal .. $queryEndLocal")
        Log.i(TAG, "Raw query instant boundaries: $queryStartInstant .. $queryEndInstant")

        try {
            val pagedResult = client.readAllRecords(
                recordType = HydrationRecord::class,
                timeRangeFilter = TimeRangeFilter.between(queryStartInstant, queryEndInstant),
            )

            val records = pagedResult.records
            Log.i(TAG, "Raw HydrationRecord pages retrieved: ${pagedResult.pagesRetrieved}")
            Log.i(TAG, "Raw HydrationRecord total count: ${records.size}")

            val origins = records.mapNotNull { it.metadata.dataOrigin.packageName }.distinct()
            Log.i(TAG, "Distinct data origins: $origins")

            records.forEachIndexed { index, record ->
                val volume = record.volume
                val origin = record.metadata.dataOrigin.packageName
                val device = record.metadata.device
                Log.i(TAG, "Record ${index + 1}:")
                Log.i(TAG, "  startTime=${record.startTime}")
                Log.i(TAG, "  endTime=${record.endTime}")
                Log.i(TAG, "  startZoneOffset=${record.startZoneOffset}")
                Log.i(TAG, "  endZoneOffset=${record.endZoneOffset}")
                Log.i(TAG, "  liters=${formatLiters(volume.inLiters)}")
                Log.i(TAG, "  milliliters=${formatMilliliters(volume.inMilliliters)}")
                Log.i(TAG, "  origin=$origin")
                if (device != null) {
                    Log.i(TAG, "  device.manufacturer=${device.manufacturer}")
                    Log.i(TAG, "  device.model=${device.model}")
                    Log.i(TAG, "  device.type=${device.type}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Raw HydrationRecord query failed: ${e.javaClass.simpleName}: ${e.message}", e)
        }

        logRawAggregate(client, nov3, zoneId)
        logRawAggregate(client, nov13, zoneId)

        Log.i(TAG, "=== End raw hydration investigation ===")
    }

    private suspend fun logPermissionState(permissionManager: HealthConnectPermissionManager?) {
        if (permissionManager == null) {
            Log.i(TAG, "Permission manager unavailable")
            return
        }

        val requiresHydration = permissionManager.requiredPermissions.contains(
            PulseHealthPermission.HYDRATION,
        )
        Log.i(TAG, "READ_HYDRATION in required permissions: $requiresHydration")

        try {
            val state = permissionManager.checkPermissions()
            Log.i(TAG, "Runtime permission state: $state")
        } catch (e: Exception) {
            Log.e(TAG, "Permission check failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private suspend fun logRawAggregate(
        client: HealthConnectClient,
        date: LocalDate,
        zoneId: ZoneId,
    ) {
        val start = date.atStartOfDay(zoneId).toInstant()
        val end = date.plusDays(1).atStartOfDay(zoneId).toInstant()

        try {
            val request = AggregateRequest(
                metrics = setOf(HydrationRecord.VOLUME_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end),
            )
            val result = client.aggregate(request)
            val volume = result[HydrationRecord.VOLUME_TOTAL]
            val origins = result.dataOrigins.map { it.packageName }

            if (volume != null) {
                Log.i(
                    TAG,
                    "Raw aggregate for $date: liters=${formatLiters(volume.inLiters)}, " +
                        "origins=$origins",
                )
            } else {
                Log.i(TAG, "Raw aggregate for $date: no volume returned (null)")
            }
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Raw aggregate for $date failed: ${e.javaClass.simpleName}: ${e.message}",
            )
        }
    }

    private fun formatLiters(liters: Double): String {
        return String.format(Locale.US, "%.10f", liters)
    }

    private fun formatMilliliters(milliliters: Double): String {
        return String.format(Locale.US, "%.6f", milliliters)
    }
}
