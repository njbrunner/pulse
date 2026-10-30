package com.pulse.app.debug

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import com.pulse.app.health.data.HealthConnectDataRepository
import com.pulse.app.health.data.HealthDataResult
import com.pulse.app.health.data.cache.RoomHealthDataCache
import com.pulse.app.health.data.model.SleepSummary
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * Temporary debug helper to verify the Health Connect -> normalization -> repository
 * heart-rate flow on a physical device.
 *
 * This class lives in the debug source set and is intentionally not available in
 * release builds.
 *
 * It queries Health Connect directly (in addition to the repository path) to expose
 * raw record counts, values, timestamps, and data origins so that discrepancies
 * between Pulse and Google Health can be investigated without changing production
 * behavior.
 */
internal object HeartRateDebugVerification {

    private const val TAG = "PulseHeartDebug"

    fun trigger(activity: ComponentActivity) {
        activity.lifecycleScope.launch {
            val context = activity.applicationContext
            val sdkStatus = HealthConnectClient.getSdkStatus(context)
            if (sdkStatus != HealthConnectClient.SDK_AVAILABLE) {
                Log.i(TAG, "Health Connect SDK unavailable: status=$sdkStatus")
                return@launch
            }

            val client = HealthConnectClient.getOrCreate(context)
            val cache = RoomHealthDataCache(context)
            val repository = HealthConnectDataRepository(context, cache)

            val today = LocalDate.now()
            val rangeStart = today.minusDays(6)
            val zoneId = ZoneId.systemDefault()
            val queryStart = rangeStart.atStartOfDay(zoneId).toInstant()
            val queryEnd = today.plusDays(1).atStartOfDay(zoneId).toInstant()
            val timeRangeFilter = TimeRangeFilter.between(queryStart, queryEnd)

            Log.i(
                TAG,
                "Triggering debug heart-rate verification: today=$today, range=$rangeStart..$today",
            )

            val sleepSummaries = when (
                val sleepResult = repository.readSleepRange(rangeStart, today)
            ) {
                is HealthDataResult.Success -> sleepResult.data
                is HealthDataResult.PermissionDenied -> {
                    Log.i(TAG, "Sleep permission denied; skipping sleep-dependent diagnostics")
                    emptyList()
                }

                is HealthDataResult.Failure -> {
                    logFailure("Sleep query", sleepResult.error)
                    emptyList()
                }

                is HealthDataResult.Unavailable -> emptyList()
            }
            logSleepPrimaryIntervals(sleepSummaries)

            logRawRestingHeartRate(client, timeRangeFilter, zoneId)
            logRawHeartRate(client, timeRangeFilter, zoneId, sleepSummaries)
            logRawHeartRateVariability(client, timeRangeFilter, zoneId)

            verifyRestingHeartRate(repository, today, rangeStart)
            verifyHeartRateVariability(repository, today, rangeStart)
        }
    }

    private fun logSleepPrimaryIntervals(sleepSummaries: List<SleepSummary>) {
        Log.i(TAG, "=== Primary sleep intervals used for HRV/RHR investigation ===")
        if (sleepSummaries.isEmpty()) {
            Log.i(TAG, "No sleep data available for the range.")
            return
        }
        sleepSummaries.forEach { sleep ->
            Log.i(
                TAG,
                "Date ${sleep.date}: start=${sleep.primarySessionStart} " +
                    "end=${sleep.primarySessionEnd} " +
                    "duration=${sleep.duration}",
            )
        }
    }

    private suspend fun logRawRestingHeartRate(
        client: HealthConnectClient,
        timeRangeFilter: TimeRangeFilter,
        zoneId: ZoneId,
    ) {
        Log.i(TAG, "=== Raw RestingHeartRateRecord query ===")

        try {
            val request = ReadRecordsRequest(
                recordType = RestingHeartRateRecord::class,
                timeRangeFilter = timeRangeFilter,
            )
            val response = client.readRecords(request)
            val records = response.records
            Log.i(TAG, "Total RestingHeartRateRecord count: ${records.size}")

            if (records.isEmpty()) {
                Log.i(TAG, "No RestingHeartRateRecord objects found in the query window.")
                return
            }

            val byDate = records.groupBy { it.time.atZone(it.zoneOffset ?: zoneId).toLocalDate() }
            byDate.toSortedMap().forEach { (date, dayRecords) ->
                Log.i(TAG, "Date $date: ${dayRecords.size} RestingHeartRateRecord(s)")
                dayRecords.forEachIndexed { index, record ->
                    Log.i(
                        TAG,
                        "  [$index] time=${record.time} " +
                            "zoneOffset=${record.zoneOffset} " +
                            "bpm=${record.beatsPerMinute} " +
                            "origin=${record.metadata.dataOrigin.packageName} " +
                            "deviceType=${record.metadata.device?.type}",
                    )
                }
                val averageBpm = dayRecords.sumOf { it.beatsPerMinute }.toDouble() / dayRecords.size
                Log.i(
                    TAG,
                    "  -> All ${dayRecords.size} record(s) contribute to normalized value (average=${
                        String.format(Locale.US, "%.1f", averageBpm)
                    })",
                )
            }
        } catch (e: SecurityException) {
            Log.i(TAG, "RestingHeartRateRecord query failed: SecurityException / permission denied")
        } catch (e: Exception) {
            Log.e(TAG, "RestingHeartRateRecord query failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private suspend fun logRawHeartRate(
        client: HealthConnectClient,
        timeRangeFilter: TimeRangeFilter,
        zoneId: ZoneId,
        sleepSummaries: List<SleepSummary>,
    ) {
        Log.i(TAG, "=== Raw HeartRateRecord query (investigate RHR source) ===")

        try {
            val request = ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = timeRangeFilter,
            )
            val response = client.readRecords(request)
            val records = response.records
            Log.i(TAG, "Total HeartRateRecord count: ${records.size}")

            if (records.isEmpty()) {
                Log.i(TAG, "No HeartRateRecord objects found in the query window.")
                return
            }

            records.forEachIndexed { index, record ->
                Log.i(
                    TAG,
                    "  [$index] start=${record.startTime} end=${record.endTime} " +
                        "sampleCount=${record.samples.size} " +
                        "origin=${record.metadata.dataOrigin.packageName} " +
                        "deviceType=${record.metadata.device?.type}",
                )
            }

            val samples = records.flatMap { record ->
                record.samples.map { sample ->
                    Triple(sample.time, sample.beatsPerMinute, record.metadata.dataOrigin.packageName)
                }
            }
            val sleepByDate = sleepSummaries.associateBy { it.date }
            val byDate = samples.groupBy { it.first.atZone(zoneId).toLocalDate() }
            byDate.toSortedMap().forEach { (date, daySamples) ->
                val bpms = daySamples.map { it.second }
                val min = bpms.minOrNull()
                val max = bpms.maxOrNull()
                val avg = bpms.average()
                val preview = bpms.take(10).joinToString(", ") { it.toString() }
                Log.i(
                    TAG,
                    "Date $date: ${daySamples.size} HeartRate sample(s), " +
                        "min=$min max=$max avg=${
                            String.format(Locale.US, "%.1f", avg)
                        } values=[$preview${if (daySamples.size > 10) ", ..." else ""}]",
                )

                val sleep = sleepByDate[date]
                val sleepStart = sleep?.primarySessionStart
                val sleepEnd = sleep?.primarySessionEnd
                if (sleepStart != null && sleepEnd != null) {
                    val sleepSamples = daySamples.filter { it.first in sleepStart..sleepEnd }
                    if (sleepSamples.isEmpty()) {
                        Log.i(TAG, "  No HeartRate samples during primary sleep interval")
                    } else {
                        val sleepBpms = sleepSamples.map { it.second }
                        val sleepMin = sleepBpms.minOrNull()
                        val sleepMax = sleepBpms.maxOrNull()
                        val sleepAvg = sleepBpms.average()
                        Log.i(
                            TAG,
                            "  Sleep-period HR samples: count=${sleepSamples.size} " +
                                "min=$sleepMin max=$sleepMax avg=${
                                    String.format(Locale.US, "%.1f", sleepAvg)
                                }",
                        )
                    }
                } else {
                    Log.i(TAG, "  No primary sleep interval available for this date")
                }
            }
        } catch (e: SecurityException) {
            Log.i(TAG, "HeartRateRecord query failed: SecurityException / permission denied")
        } catch (e: Exception) {
            Log.e(TAG, "HeartRateRecord query failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private suspend fun logRawHeartRateVariability(
        client: HealthConnectClient,
        timeRangeFilter: TimeRangeFilter,
        zoneId: ZoneId,
    ) {
        Log.i(TAG, "=== Raw HeartRateVariabilityRmssdRecord query ===")

        try {
            val request = ReadRecordsRequest(
                recordType = HeartRateVariabilityRmssdRecord::class,
                timeRangeFilter = timeRangeFilter,
            )
            val response = client.readRecords(request)
            val records = response.records
            Log.i(TAG, "Total HeartRateVariabilityRmssdRecord count: ${records.size}")

            if (records.isEmpty()) {
                Log.i(TAG, "No HeartRateVariabilityRmssdRecord objects found in the query window.")
                return
            }

            val byDate = records.groupBy { it.time.atZone(it.zoneOffset ?: zoneId).toLocalDate() }
            byDate.toSortedMap().forEach { (date, dayRecords) ->
                Log.i(TAG, "Date $date: ${dayRecords.size} HeartRateVariabilityRmssdRecord(s)")
                dayRecords.forEachIndexed { index, record ->
                    Log.i(
                        TAG,
                        "  [$index] time=${record.time} " +
                            "zoneOffset=${record.zoneOffset} " +
                            "ms=${record.heartRateVariabilityMillis} " +
                            "origin=${record.metadata.dataOrigin.packageName} " +
                            "deviceType=${record.metadata.device?.type}",
                    )
                }
                val averageMs = dayRecords.sumOf { it.heartRateVariabilityMillis } / dayRecords.size
                Log.i(
                    TAG,
                    "  -> All ${dayRecords.size} record(s) contribute to normalized value (average=${
                        String.format(Locale.US, "%.1f", averageMs)
                    })",
                )
            }
        } catch (e: SecurityException) {
            Log.i(TAG, "HeartRateVariabilityRmssdRecord query failed: SecurityException / permission denied")
        } catch (e: Exception) {
            Log.e(TAG, "HeartRateVariabilityRmssdRecord query failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private suspend fun verifyRestingHeartRate(
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
        Log.i(TAG, "=== Repository Heart Rate Variability ===")

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
