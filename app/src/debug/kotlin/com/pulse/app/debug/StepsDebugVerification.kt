package com.pulse.app.debug

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import com.pulse.app.health.data.HealthConnectDataRepository
import com.pulse.app.health.data.HealthDataResult
import com.pulse.app.health.data.StepsDataNormalizer
import com.pulse.app.health.data.readAllRecords
import com.pulse.app.health.data.model.StepsSummary
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Temporary debug helper to verify the Health Connect -> normalization -> repository
 * steps flow on a physical device.
 *
 * This class lives in the debug source set and is intentionally not available in
 * release builds.
 *
 * In addition to exercising the production repository path, it performs a raw,
 * paginated Health Connect query so we can compare raw record counts, data
 * origins, overlapping records, and midnight splits against the normalized Pulse
 * totals. This extra raw inspection is debug-only and is tracked for removal.
 */
internal object StepsDebugVerification {

    private const val TAG = "PulseStepsDebug"
    private const val PAGE_SIZE = 1000

    fun trigger(activity: ComponentActivity, repository: HealthConnectDataRepository) {
        activity.lifecycleScope.launch {
            val context = activity.applicationContext
            val sdkStatus = HealthConnectClient.getSdkStatus(context)
            if (sdkStatus != HealthConnectClient.SDK_AVAILABLE) {
                Log.i(TAG, "Health Connect SDK unavailable: status=$sdkStatus")
                return@launch
            }

            val client = HealthConnectClient.getOrCreate(context)
            val today = LocalDate.now()
            val rangeStart = today.minusDays(6)
            val zoneId = ZoneId.systemDefault()

            Log.i(TAG, "Triggering PulseStepsDebug verification: today=$today, range=$rangeStart..$today")

            logRepositoryOutput(repository, today, rangeStart)

            logRawStepsDiagnostics(
                client = client,
                rangeStart = rangeStart,
                rangeEnd = today,
                zoneId = zoneId,
                repository = repository,
            )
        }
    }

    private suspend fun logRepositoryOutput(
        repository: HealthConnectDataRepository,
        today: LocalDate,
        rangeStart: LocalDate,
    ) {
        Log.i(TAG, "=== Repository Steps (production path) ===")

        when (val result = repository.readSteps(today)) {
            is HealthDataResult.Success ->
                Log.i(TAG, "Today ($today): Success - steps=${result.data.steps}")

            is HealthDataResult.Unavailable ->
                Log.i(TAG, "Today ($today): Unavailable")

            is HealthDataResult.PermissionDenied ->
                Log.i(TAG, "Today ($today): PermissionDenied")

            is HealthDataResult.Failure ->
                Log.e(TAG, "Today ($today): Failure: ${result.error.javaClass.simpleName}: ${result.error.message}")
        }

        val normalizedByDate = mutableMapOf<LocalDate, Long>()
        when (val result = repository.readStepsRange(rangeStart, today)) {
            is HealthDataResult.Success -> {
                Log.i(TAG, "Range ($rangeStart..$today): Success - ${result.data.size} day(s)")
                result.data.forEach { summary ->
                    Log.i(TAG, "  ${summary.date}: steps=${summary.steps}")
                    normalizedByDate[summary.date] = summary.steps
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

    private suspend fun logRawStepsDiagnostics(
        client: HealthConnectClient,
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
        zoneId: ZoneId,
        repository: HealthConnectDataRepository,
    ) {
        Log.i(TAG, "=== Raw StepsRecord Diagnostic ===")

        val queryStart = rangeStart.atStartOfDay(zoneId).toInstant()
        val queryEnd = rangeEnd.plusDays(1).atStartOfDay(zoneId).toInstant()
        val timeRangeFilter = TimeRangeFilter.between(queryStart, queryEnd)

        Log.i(TAG, "Query window: start=$queryStart end=$queryEnd pageSize=$PAGE_SIZE")

        val pagedResult = try {
            client.readAllRecords(
                recordType = StepsRecord::class,
                timeRangeFilter = timeRangeFilter,
                pageSize = PAGE_SIZE,
            )
        } catch (e: SecurityException) {
            Log.i(TAG, "Raw StepsRecord query failed: SecurityException / permission denied")
            return
        } catch (e: Exception) {
            Log.e(TAG, "Raw StepsRecord query failed: ${e.javaClass.simpleName}: ${e.message}")
            return
        }

        val records = pagedResult.records
        Log.i(
            TAG,
            "Retrieved ${pagedResult.pagesRetrieved} page(s), ${records.size} StepsRecord object(s)",
        )

        if (records.isEmpty()) {
            Log.i(TAG, "No StepsRecord objects found in the query window.")
            return
        }

        logOrigins(records)
        logCrossMidnightRecords(records, zoneId)
        logDailySummaries(records, rangeStart, rangeEnd, zoneId, repository)
    }

    private fun logOrigins(records: List<StepsRecord>) {
        Log.i(TAG, "=== Data origins ===")
        val byOrigin = records.groupBy { it.metadata.dataOrigin.packageName }
        val sortedOrigins = byOrigin.keys.sorted()
        sortedOrigins.forEach { origin ->
            val originRecords = byOrigin.getValue(origin)
            val totalSteps = originRecords.sumOf { it.count }
            Log.i(TAG, "  $origin: records=${originRecords.size}, rawSteps=$totalSteps")
        }
    }

    private fun logCrossMidnightRecords(records: List<StepsRecord>, zoneId: ZoneId) {
        Log.i(TAG, "=== Cross-midnight records ===")
        val crossingRecords = records.filter {
            val startDate = it.startTime.atZone(it.startZoneOffset ?: zoneId).toLocalDate()
            val endDate = it.endTime.atZone(it.endZoneOffset ?: zoneId).toLocalDate()
            startDate != endDate
        }

        Log.i(TAG, "Cross-midnight record count: ${crossingRecords.size}")
        if (crossingRecords.isEmpty()) {
            return
        }

        val totalCrossingSteps = crossingRecords.sumOf { it.count }
        Log.i(TAG, "Total steps in cross-midnight records: $totalCrossingSteps")

        crossingRecords.sortedBy { it.startTime }.forEach { record ->
            val startDate = record.startTime.atZone(record.startZoneOffset ?: zoneId).toLocalDate()
            val endDate = record.endTime.atZone(record.endZoneOffset ?: zoneId).toLocalDate()
            val split = StepsDataNormalizer.normalize(listOf(record), zoneId)
            val splitText = split.joinToString { "${it.date}=${it.steps}" }
            Log.i(
                TAG,
                "  $startDate $endDate count=${record.count} " +
                    "start=${record.startTime} end=${record.endTime} split=[$splitText]",
            )
        }
    }

    private suspend fun logDailySummaries(
        records: List<StepsRecord>,
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
        zoneId: ZoneId,
        repository: HealthConnectDataRepository,
    ) {
        Log.i(TAG, "=== Per-day raw vs normalized ===")

        val normalizedByDate = when (
            val result = repository.readStepsRange(rangeStart, rangeEnd)
        ) {
            is HealthDataResult.Success -> result.data.associateBy { it.date }
            else -> emptyMap()
        }

        var date = rangeStart
        while (!date.isAfter(rangeEnd)) {
            val dayStart = date.atStartOfDay(zoneId).toInstant()
            val dayEnd = date.plusDays(1).atStartOfDay(zoneId).toInstant()

            val overlapping = records.filter { it.startTime < dayEnd && it.endTime > dayStart }
            val rawSteps = overlapping.sumOf { it.count }
            val origins = overlapping
                .map { it.metadata.dataOrigin.packageName }
                .distinct()
                .sorted()
            val normalizedSteps = normalizedByDate[date]?.steps

            val overlapInfo = analyzeOverlaps(overlapping)

            Log.i(
                TAG,
                "Date $date: rawRecords=${overlapping.size}, rawSteps=$rawSteps, " +
                    "normalized=$normalizedSteps, origins=$origins " +
                    "overlappingPairs=${overlapInfo.overlappingPairs}, " +
                    "duplicateCandidates=${overlapInfo.duplicateCandidates}",
            )

            if (overlapInfo.details.isNotEmpty()) {
                overlapInfo.details.forEach { detail ->
                    Log.i(TAG, "    $detail")
                }
            }

            date = date.plusDays(1)
        }
    }

    private fun analyzeOverlaps(records: List<StepsRecord>): OverlapInfo {
        if (records.size < 2) {
            return OverlapInfo(0, 0, emptyList())
        }

        val sorted = records.sortedBy { it.startTime }
        var overlappingPairs = 0
        val duplicateCandidates = mutableSetOf<String>()
        val details = mutableListOf<String>()

        for (i in sorted.indices) {
            val a = sorted[i]
            for (j in i + 1 until sorted.size) {
                val b = sorted[j]
                if (b.startTime >= a.endTime) break

                overlappingPairs++
                val originA = a.metadata.dataOrigin.packageName
                val originB = b.metadata.dataOrigin.packageName
                val sameOrigin = originA == originB
                details.add(
                    "overlap: ${a.startTime}..${a.endTime}($originA, ${a.count}) vs " +
                        "${b.startTime}..${b.endTime}($originB, ${b.count})",
                )

                if (a.startTime == b.startTime && a.endTime == b.endTime && a.count == b.count) {
                    duplicateCandidates.add("exact(start=${a.startTime}, end=${a.endTime}, count=${a.count}, origin=$originA)")
                    if (!sameOrigin) {
                        duplicateCandidates.add("sameValuesDifferentOrigin(start=${a.startTime}, end=${a.endTime}, count=${a.count})")
                    }
                }
            }
        }

        return OverlapInfo(
            overlappingPairs = overlappingPairs,
            duplicateCandidates = duplicateCandidates.size,
            details = details,
        )
    }

    private data class OverlapInfo(
        val overlappingPairs: Int,
        val duplicateCandidates: Int,
        val details: List<String>,
    )
}
