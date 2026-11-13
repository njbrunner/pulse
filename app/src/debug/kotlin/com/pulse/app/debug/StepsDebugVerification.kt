package com.pulse.app.debug

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import com.pulse.app.health.data.HealthConnectDataRepository
import com.pulse.app.health.data.HealthDataResult
import com.pulse.app.health.data.readAllRecords
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Temporary debug helper to verify the production repository Steps path on a
 * physical device and to investigate the Nov 11 aggregate discrepancy.
 *
 * This class lives in the debug source set and is intentionally not available
 * in release builds.
 *
 * It exercises the real repository for the production path and uses a direct
 * HealthConnectClient for diagnostic queries only. No production behavior is
 * changed. Removal before release is tracked.
 */
internal object StepsDebugVerification {

    private const val TAG = "PulseStepsDebug"
    private val INVESTIGATION_DATE: LocalDate = LocalDate.of(2026, 11, 11)

    fun trigger(activity: ComponentActivity, repository: HealthConnectDataRepository) {
        activity.lifecycleScope.launch {
            val today = LocalDate.now()
            val rangeStart = today.minusDays(6)

            Log.i(TAG, "Triggering PulseStepsDebug verification: today=$today, range=$rangeStart..$today")

            logToday(repository, today)
            logRange(repository, rangeStart, today)
            runNov11Investigation(repository, activity)
        }
    }

    private suspend fun runNov11Investigation(
        repository: HealthConnectDataRepository,
        activity: ComponentActivity,
    ) {
        Log.i(TAG, "=== Nov 11 aggregate investigation ===")

        val zoneId = ZoneId.systemDefault()
        val localStart = INVESTIGATION_DATE.atStartOfDay(zoneId)
        val localEnd = INVESTIGATION_DATE.plusDays(1).atStartOfDay(zoneId)
        val startInstant = localStart.toInstant()
        val endInstant = localEnd.toInstant()

        Log.i(TAG, "Zone/boundaries:")
        Log.i(TAG, "  requestedDate=$INVESTIGATION_DATE")
        Log.i(TAG, "  zoneId=$zoneId")
        Log.i(TAG, "  localStart=$localStart")
        Log.i(TAG, "  localEnd=$localEnd")
        Log.i(TAG, "  startInstant=$startInstant")
        Log.i(TAG, "  endInstant=$endInstant")

        try {
            logProductionAggregate(repository)
        } catch (e: Exception) {
            Log.e(TAG, "Production aggregate failed", e)
        }

        val client = when (HealthConnectClient.getSdkStatus(activity)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectClient.getOrCreate(activity)
            else -> {
                Log.w(TAG, "Health Connect unavailable; skipping direct client diagnostics")
                Log.i(TAG, "=== End Nov 11 investigation ===")
                return
            }
        }

        val discoveredOrigins = try {
            logRawRecords(client, startInstant, endInstant)
        } catch (e: Exception) {
            Log.e(TAG, "Raw record query failed", e)
            emptySet<DataOrigin>()
        }

        try {
            logPerOriginAggregates(client, startInstant, endInstant, discoveredOrigins)
        } catch (e: Exception) {
            Log.e(TAG, "Per-origin aggregate query failed", e)
        }

        try {
            logGroupedPeriodAggregate(client)
        } catch (e: Exception) {
            Log.e(TAG, "Grouped-period aggregate query failed", e)
        }

        try {
            logAdjacentDayComparison(client, zoneId)
        } catch (e: Exception) {
            Log.e(TAG, "Adjacent-day comparison failed", e)
        }

        Log.i(TAG, "=== End Nov 11 investigation ===")
    }

    private suspend fun logProductionAggregate(repository: HealthConnectDataRepository) {
        when (val result = repository.readSteps(INVESTIGATION_DATE)) {
            is HealthDataResult.Success ->
                Log.i(
                    TAG,
                    "Production/unfiltered aggregate for $INVESTIGATION_DATE: ${result.data.steps}"
                )

            is HealthDataResult.Unavailable ->
                Log.i(TAG, "Production/unfiltered aggregate for $INVESTIGATION_DATE: Unavailable")

            is HealthDataResult.PermissionDenied ->
                Log.i(TAG, "Production/unfiltered aggregate for $INVESTIGATION_DATE: PermissionDenied")

            is HealthDataResult.Failure ->
                Log.e(
                    TAG,
                    "Production/unfiltered aggregate for $INVESTIGATION_DATE: Failure",
                    result.error
                )
        }
    }

    private suspend fun logRawRecords(
        client: HealthConnectClient,
        startInstant: Instant,
        endInstant: Instant,
    ): Set<DataOrigin> {
        val timeRange = TimeRangeFilter.between(startInstant, endInstant)
        val pagedResult = client.readAllRecords(
            recordType = StepsRecord::class,
            timeRangeFilter = timeRange,
            pageSize = 1000,
        )
        val records = pagedResult.records
        val byOrigin = records.groupBy { it.metadata.dataOrigin.packageName }
        val totalRaw = records.sumOf { it.count }

        Log.i(TAG, "Raw StepsRecords touching $INVESTIGATION_DATE:")
        Log.i(TAG, "  totalPages=${pagedResult.pagesRetrieved}")
        Log.i(TAG, "  totalRecords=${records.size}")
        Log.i(TAG, "  totalRawCount=$totalRaw")
        Log.i(TAG, "  distinctOrigins=${byOrigin.keys.sorted().joinToString()}")

        byOrigin.toSortedMap().forEach { (packageName, originRecords) ->
            val count = originRecords.size
            val rawSteps = originRecords.sumOf { it.count }
            val first = originRecords.minOf { it.startTime }
            val last = originRecords.maxOf { it.endTime }
            Log.i(
                TAG,
                "  origin=$packageName records=$count rawSteps=$rawSteps first=$first last=$last"
            )
        }

        val origins = byOrigin.keys.toList().sorted()
        if (origins.size >= 2) {
            val firstRecords = byOrigin.getValue(origins[0])
            val secondRecords = byOrigin.getValue(origins[1])
            val firstStart = firstRecords.minOf { it.startTime }
            val firstEnd = firstRecords.maxOf { it.endTime }
            val secondStart = secondRecords.minOf { it.startTime }
            val secondEnd = secondRecords.maxOf { it.endTime }
            val overlapStart = maxOf(firstStart, secondStart)
            val overlapEnd = minOf(firstEnd, secondEnd)
            val overlapMinutes = if (overlapEnd.isAfter(overlapStart)) {
                ChronoUnit.MINUTES.between(overlapStart, overlapEnd)
            } else {
                0
            }
            Log.i(TAG, "Temporal overlap between origins:")
            Log.i(TAG, "  ${origins[0]} span: $firstStart .. $firstEnd")
            Log.i(TAG, "  ${origins[1]} span: $secondStart .. $secondEnd")
            Log.i(TAG, "  overlap: $overlapStart .. $overlapEnd (${overlapMinutes}min)")
        }

        return records.map { it.metadata.dataOrigin }.distinct().toSet()
    }

    private suspend fun logPerOriginAggregates(
        client: HealthConnectClient,
        startInstant: Instant,
        endInstant: Instant,
        discoveredOrigins: Set<DataOrigin>,
    ) {
        val timeRange = TimeRangeFilter.between(startInstant, endInstant)

        val unfiltered = client.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = timeRange,
            )
        )
        Log.i(TAG, "Per-origin aggregates for $INVESTIGATION_DATE:")
        Log.i(TAG, "  allOrigins: ${unfiltered[StepsRecord.COUNT_TOTAL]}")
        Log.i(
            TAG,
            "  allOrigins.dataOrigins=${unfiltered.dataOrigins.map { it.packageName }.sorted()}"
        )

        discoveredOrigins.sortedBy { it.packageName }.forEach { origin ->
            val filtered = client.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = timeRange,
                    dataOriginFilter = setOf(origin),
                )
            )
            Log.i(
                TAG,
                "  ${origin.packageName}: ${filtered[StepsRecord.COUNT_TOTAL]} " +
                    "(dataOrigins=${filtered.dataOrigins.map { it.packageName }})"
            )
        }
    }

    private suspend fun logGroupedPeriodAggregate(client: HealthConnectClient) {
        val request = AggregateGroupByPeriodRequest(
            metrics = setOf(StepsRecord.COUNT_TOTAL),
            timeRangeFilter = TimeRangeFilter.between(
                INVESTIGATION_DATE.atStartOfDay(),
                INVESTIGATION_DATE.plusDays(1).atStartOfDay(),
            ),
            timeRangeSlicer = Period.ofDays(1),
        )
        val grouped = client.aggregateGroupByPeriod(request)
        val bucket = grouped.find { it.startTime.toLocalDate() == INVESTIGATION_DATE }

        Log.i(TAG, "Grouped-period aggregate for $INVESTIGATION_DATE:")
        if (bucket != null) {
            Log.i(TAG, "  value=${bucket.result[StepsRecord.COUNT_TOTAL]}")
            Log.i(
                TAG,
                "  dataOrigins=${bucket.result.dataOrigins.map { it.packageName }.sorted()}"
            )
        } else {
            Log.i(TAG, "  no bucket found")
        }
    }

    private suspend fun logAdjacentDayComparison(client: HealthConnectClient, zoneId: ZoneId) {
        Log.i(TAG, "Adjacent-day aggregate comparison:")
        listOf(
            INVESTIGATION_DATE.minusDays(1),
            INVESTIGATION_DATE,
            INVESTIGATION_DATE.plusDays(1),
        ).forEach { date ->
            val dayStartInstant = date.atStartOfDay(zoneId).toInstant()
            val dayEndInstant = date.plusDays(1).atStartOfDay(zoneId).toInstant()

            val single = client.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(dayStartInstant, dayEndInstant),
                )
            )

            val grouped = client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(
                        date.atStartOfDay(),
                        date.plusDays(1).atStartOfDay(),
                    ),
                    timeRangeSlicer = Period.ofDays(1),
                )
            ).find { it.startTime.toLocalDate() == date }

            Log.i(
                TAG,
                "  $date single=${single[StepsRecord.COUNT_TOTAL]} " +
                    "grouped=${grouped?.result?.get(StepsRecord.COUNT_TOTAL)}"
            )
        }
    }

    private suspend fun logToday(
        repository: HealthConnectDataRepository,
        today: LocalDate,
    ) {
        val label = "Today ($today)"
        when (val result = repository.readSteps(today)) {
            is HealthDataResult.Success ->
                Log.i(TAG, "$label: Success - steps=${result.data.steps}")

            is HealthDataResult.Unavailable ->
                Log.i(TAG, "$label: Unavailable")

            is HealthDataResult.PermissionDenied ->
                Log.i(TAG, "$label: PermissionDenied")

            is HealthDataResult.Failure ->
                Log.e(TAG, "$label: Failure: ${result.error.javaClass.simpleName}: ${result.error.message}")
        }
    }

    private suspend fun logRange(
        repository: HealthConnectDataRepository,
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
    ) {
        val label = "Range ($rangeStart..$rangeEnd)"
        when (val result = repository.readStepsRange(rangeStart, rangeEnd)) {
            is HealthDataResult.Success -> {
                Log.i(TAG, "$label: Success - ${result.data.size} day(s)")
                result.data.forEach { summary ->
                    Log.i(TAG, "  ${summary.date}: steps=${summary.steps}")
                }
            }

            is HealthDataResult.Unavailable ->
                Log.i(TAG, "$label: Unavailable")

            is HealthDataResult.PermissionDenied ->
                Log.i(TAG, "$label: PermissionDenied")

            is HealthDataResult.Failure ->
                Log.e(TAG, "$label: Failure: ${result.error.javaClass.simpleName}: ${result.error.message}")
        }
    }
}
