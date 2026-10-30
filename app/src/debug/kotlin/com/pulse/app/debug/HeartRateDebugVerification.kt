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
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
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

    // Health Connect documents HeartRateRecord.Sample.beatsPerMinute as having a
    // valid range of 1-300 BPM. Values outside this range are treated as invalid
    // and excluded from Pulse RHR candidate calculations.
    private const val MIN_VALID_BPM = 1L
    private const val MAX_VALID_BPM = 300L

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
            // Expand the query window by one day before the requested range because the
            // primary sleep session for the first requested day can start on the previous
            // calendar date. The end is already the day after the requested range, which
            // covers sleep periods ending on the final requested wake date.
            val queryStart = rangeStart.minusDays(1).atStartOfDay(zoneId).toInstant()
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
            logRawHeartRate(client, timeRangeFilter, sleepSummaries, rangeStart, today, zoneId)
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
            val origins = records.map { it.metadata.dataOrigin.packageName }.distinct().sorted()
            Log.i(TAG, "Data origins: $origins")

            byDate.toSortedMap().forEach { (date, dayRecords) ->
                val averageBpm = dayRecords.sumOf { it.beatsPerMinute }.toDouble() / dayRecords.size
                Log.i(
                    TAG,
                    "Date $date: count=${dayRecords.size} average=${formatOneDecimal(averageBpm)}",
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
        sleepSummaries: List<SleepSummary>,
        rangeStart: LocalDate,
        today: LocalDate,
        zoneId: ZoneId,
    ) {
        Log.i(TAG, "=== Raw HeartRateRecord query (investigate RHR source) ===")

        try {
            val pageSize = 1000
            Log.i(
                TAG,
                "Query window: start=${timeRangeFilter.startTime} end=${timeRangeFilter.endTime} " +
                    "pageSize=$pageSize",
            )

            val pagedResult = client.readAllRecords(
                recordType = HeartRateRecord::class,
                timeRangeFilter = timeRangeFilter,
                pageSize = pageSize,
            )
            val records = pagedResult.records
            val pagesRetrieved = pagedResult.pagesRetrieved

            Log.i(
                TAG,
                "Retrieved $pagesRetrieved page(s), ${records.size} HeartRateRecord object(s)",
            )

            if (records.isEmpty()) {
                Log.i(TAG, "No HeartRateRecord objects found in the query window.")
                return
            }

            val samples = records.flatMap { record ->
                record.samples.map { sample ->
                    HeartRateSample(
                        time = sample.time,
                        bpm = sample.beatsPerMinute,
                        origin = record.metadata.dataOrigin.packageName,
                    )
                }
            }

            val validSamples = samples.filter { it.bpm in MIN_VALID_BPM..MAX_VALID_BPM }
            if (validSamples.size < samples.size) {
                Log.i(
                    TAG,
                    "Excluded ${samples.size - validSamples.size} sample(s) outside valid BPM range " +
                        "[$MIN_VALID_BPM, $MAX_VALID_BPM]",
                )
            }

            val earliest = validSamples.minByOrNull { it.time }?.time
            val latest = validSamples.maxByOrNull { it.time }?.time
            val origins = validSamples.map { it.origin }.distinct().sorted()

            Log.i(
                TAG,
                "Total individual heart-rate samples: ${validSamples.size}, " +
                    "earliest=$earliest latest=$latest origins=$origins",
            )

            logSleepPeriodHeartRateStatistics(
                requestedRangeStart = rangeStart,
                requestedRangeEnd = today,
                sleepSummaries = sleepSummaries,
                samples = validSamples,
                zoneId = zoneId,
            )
        } catch (e: SecurityException) {
            Log.i(TAG, "HeartRateRecord query failed: SecurityException / permission denied")
        } catch (e: Exception) {
            Log.e(TAG, "HeartRateRecord query failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun logSleepPeriodHeartRateStatistics(
        requestedRangeStart: LocalDate,
        requestedRangeEnd: LocalDate,
        sleepSummaries: List<SleepSummary>,
        samples: List<HeartRateSample>,
        zoneId: ZoneId,
    ) {
        Log.i(TAG, "=== Sleep-period HR candidate statistics (debug only) ===")

        if (sleepSummaries.isEmpty()) {
            Log.i(TAG, "No sleep data available; no sleep-period HR statistics.")
            return
        }

        val sleepByDate = sleepSummaries.associateBy { it.date }
        var datesWithSleep = 0

        var date = requestedRangeStart
        while (!date.isAfter(requestedRangeEnd)) {
            val sleep = sleepByDate[date]
            val sleepStart = sleep?.primarySessionStart
            val sleepEnd = sleep?.primarySessionEnd

            if (sleepStart == null || sleepEnd == null) {
                Log.i(
                    TAG,
                    "Date $date: No primary sleep session; Pulse RHR cannot be calculated",
                )
                date = date.plusDays(1)
                continue
            }

            datesWithSleep++
            val sleepSamples = samples.filter { it.time in sleepStart..sleepEnd }
            if (sleepSamples.isEmpty()) {
                Log.i(
                    TAG,
                    "Date $date: No HR samples during primary sleep interval; " +
                        "Pulse RHR cannot be calculated",
                )
                date = date.plusDays(1)
                continue
            }

            val bpms = sleepSamples.map { it.bpm }
            val min = bpms.minOrNull() ?: error("non-empty list has no minimum")
            val mean = bpms.average()
            val median = percentile(bpms, 50.0)
            val p1 = percentile(bpms, 1.0)
            val p2 = percentile(bpms, 2.0)
            val p3 = percentile(bpms, 3.0)
            val p5 = percentile(bpms, 5.0)

            val fiveMinuteWindow = Duration.ofMinutes(5)
            val tenMinuteWindow = Duration.ofMinutes(10)
            val low5m = findLowestWindowMean(
                sleepSamples,
                fiveMinuteWindow,
                minimumSamplesForWindow(fiveMinuteWindow),
            )
            val low10m = findLowestWindowMean(
                sleepSamples,
                tenMinuteWindow,
                minimumSamplesForWindow(tenMinuteWindow),
            )

            val low5mText = low5m?.let { formatWindow(it, zoneId) } ?: "none"
            val low10mText = low10m?.let { formatWindow(it, zoneId) } ?: "none"

            Log.i(
                TAG,
                "Date $date: samples=${bpms.size} min=$min mean=${
                    formatOneDecimal(mean)
                } median=${formatOneDecimal(median)} p1=${
                    formatOneDecimal(p1)
                } p2=${formatOneDecimal(p2)} p3=${
                    formatOneDecimal(p3)
                } p5=${formatOneDecimal(p5)} low5m=$low5mText low10m=$low10mText",
            )

            date = date.plusDays(1)
        }

        val requestedDays = ChronoUnit.DAYS.between(requestedRangeStart, requestedRangeEnd).toInt() + 1
        Log.i(
            TAG,
            "Dates with primary sleep sessions in requested range: $datesWithSleep / $requestedDays",
        )
    }

    private fun minimumSamplesForWindow(windowDuration: Duration): Int {
        // Require at least one sample per minute of window length, with a floor of 3,
        // so a nearly empty window cannot produce an artificially low mean. This is a
        // coverage guard, not a physiological threshold.
        return maxOf(3, windowDuration.toMinutes().toInt())
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
            val origins = records.map { it.metadata.dataOrigin.packageName }.distinct().sorted()
            Log.i(TAG, "Data origins: $origins")

            byDate.toSortedMap().forEach { (date, dayRecords) ->
                val averageMs = dayRecords.sumOf { it.heartRateVariabilityMillis }.toDouble() / dayRecords.size
                Log.i(
                    TAG,
                    "Date $date: count=${dayRecords.size} average=${formatOneDecimal(averageMs)}",
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

    private fun formatOneDecimal(value: Double): String {
        return String.format(Locale.US, "%.1f", value)
    }
}
