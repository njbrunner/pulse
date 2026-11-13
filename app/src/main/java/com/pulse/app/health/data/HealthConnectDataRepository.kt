package com.pulse.app.health.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.pulse.app.health.data.cache.HealthDataCache
import com.pulse.app.health.data.model.HeartRateVariability
import com.pulse.app.health.data.model.HydrationSummary
import com.pulse.app.health.data.model.RestingHeartRate
import com.pulse.app.health.data.model.SleepSummary
import com.pulse.app.health.data.model.StepsSummary
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId

/**
 * [HealthDataRepository] implementation backed by Google Health Connect.
 *
 * Detailed per-metric retrieval logic is added incrementally per issue. Sleep
 * (#5), heart rate (#6), steps (#7), and hydration (#8) are implemented. This
 * class establishes the Health Connect boundary, result mapping, and
 * error-handling pattern.
 */
class HealthConnectDataRepository(
    private val applicationContext: Context,
    private val healthDataCache: HealthDataCache,
    private val healthConnectClient: HealthConnectClient? = null,
    private val preferredStepsDataOriginPolicy: PreferredStepsDataOriginPolicy =
        PreferredStepsDataOriginPolicy(),
) : HealthDataRepository {

    private val client: HealthConnectClient? by lazy {
        healthConnectClient ?: when (HealthConnectClient.getSdkStatus(applicationContext)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectClient.getOrCreate(applicationContext)
            else -> null
        }
    }

    override suspend fun readSleep(date: LocalDate): HealthDataResult<SleepSummary> {
        return when (val rangeResult = readSleepRange(date, date)) {
            is HealthDataResult.Success -> {
                rangeResult.data.firstOrNull()?.let { HealthDataResult.Success(it) }
                    ?: HealthDataResult.Unavailable
            }

            is HealthDataResult.Unavailable -> HealthDataResult.Unavailable
            is HealthDataResult.PermissionDenied -> HealthDataResult.PermissionDenied
            is HealthDataResult.Failure -> HealthDataResult.Failure(rangeResult.error)
        }
    }

    override suspend fun readSleepRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): HealthDataResult<List<SleepSummary>> {
        val currentClient = client ?: return HealthDataResult.Unavailable
        val zoneId = ZoneId.systemDefault()

        // Expand the query window by one day on each side so sessions that cross
        // midnight at the edges of the requested range are still captured.
        val queryStart = startDate.minusDays(1).atStartOfDay(zoneId).toInstant()
        val queryEnd = endDate.plusDays(1).atStartOfDay(zoneId).toInstant()

        return try {
            val request = ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(queryStart, queryEnd),
            )
            val response = currentClient.readRecords(request)
            val summaries = SleepDataNormalizer.normalize(response.records, zoneId)
                .filter { it.date in startDate..endDate }

            if (summaries.isEmpty()) {
                HealthDataResult.Unavailable
            } else {
                val syncedAt = Instant.now()
                healthDataCache.saveSleepRange(summaries, syncedAt)
                HealthDataResult.Success(summaries)
            }
        } catch (e: SecurityException) {
            HealthDataResult.PermissionDenied
        } catch (e: Exception) {
            HealthDataResult.Failure(e)
        }
    }

    override suspend fun readSteps(date: LocalDate): HealthDataResult<StepsSummary> {
        val currentClient = client ?: return HealthDataResult.Unavailable
        val zoneId = ZoneId.systemDefault()

        return try {
            val timeRangeFilter = TimeRangeFilter.between(
                date.atStartOfDay(zoneId).toInstant(),
                date.plusDays(1).atStartOfDay(zoneId).toInstant(),
            )

            // 1. Prefer wearable data (e.g., Fitbit) when available.
            val summary = readPreferredSteps(currentClient, timeRangeFilter, date)
                // 2. Fall back to Health Connect's standard deduplicated aggregate.
                ?: run {
                    val request = AggregateRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = timeRangeFilter,
                    )
                    val response = currentClient.aggregate(request)
                    StepsDataNormalizer.toStepsSummary(response, date)
                }

            if (summary == null) {
                HealthDataResult.Unavailable
            } else {
                val syncedAt = Instant.now()
                healthDataCache.saveSteps(summary, syncedAt)
                HealthDataResult.Success(summary)
            }
        } catch (e: SecurityException) {
            HealthDataResult.PermissionDenied
        } catch (e: Exception) {
            HealthDataResult.Failure(e)
        }
    }

    /**
     * Queries each configured preferred wearable origin for [date] and returns
     * the first origin that reports a step aggregate. `null` means no preferred
     * wearable data is available for this date.
     */
    private suspend fun readPreferredSteps(
        client: HealthConnectClient,
        timeRangeFilter: TimeRangeFilter,
        date: LocalDate,
    ): StepsSummary? {
        for (origin in preferredStepsDataOriginPolicy.knownDataOrigins()) {
            val request = AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = timeRangeFilter,
                dataOriginFilter = setOf(origin),
            )
            val response = client.aggregate(request)
            StepsDataNormalizer.toStepsSummary(response, date)?.let { return it }
        }
        return null
    }

    override suspend fun readStepsRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): HealthDataResult<List<StepsSummary>> {
        val currentClient = client ?: return HealthDataResult.Unavailable

        return try {
            val timeRangeFilter = TimeRangeFilter.between(
                startDate.atStartOfDay(),
                endDate.plusDays(1).atStartOfDay(),
            )

            // 1. Preferred wearable aggregate per day.
            val preferredByDate = mutableMapOf<LocalDate, StepsSummary>()
            for (origin in preferredStepsDataOriginPolicy.knownDataOrigins()) {
                val preferredRequest = AggregateGroupByPeriodRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = timeRangeFilter,
                    timeRangeSlicer = Period.ofDays(1),
                    dataOriginFilter = setOf(origin),
                )
                val preferredResponse = currentClient.aggregateGroupByPeriod(preferredRequest)
                StepsDataNormalizer.toStepsSummaries(preferredResponse)
                    .filter { it.date in startDate..endDate }
                    .forEach { summary -> preferredByDate.putIfAbsent(summary.date, summary) }
            }

            // 2. Fallback aggregate per day for dates without preferred data.
            val fallbackRequest = AggregateGroupByPeriodRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = timeRangeFilter,
                timeRangeSlicer = Period.ofDays(1),
            )
            val fallbackResponse = currentClient.aggregateGroupByPeriod(fallbackRequest)
            val fallbackByDate = StepsDataNormalizer.toStepsSummaries(fallbackResponse)
                .filter { it.date in startDate..endDate }
                .associateBy { it.date }

            val summaries = mutableListOf<StepsSummary>()
            var currentDate = startDate
            while (!currentDate.isAfter(endDate)) {
                val summary = preferredByDate[currentDate] ?: fallbackByDate[currentDate]
                if (summary != null) {
                    summaries.add(summary)
                }
                currentDate = currentDate.plusDays(1)
            }

            if (summaries.isEmpty()) {
                HealthDataResult.Unavailable
            } else {
                val syncedAt = Instant.now()
                healthDataCache.saveStepsRange(summaries, syncedAt)
                HealthDataResult.Success(summaries)
            }
        } catch (e: SecurityException) {
            HealthDataResult.PermissionDenied
        } catch (e: Exception) {
            HealthDataResult.Failure(e)
        }
    }

    override suspend fun readRestingHeartRate(date: LocalDate): HealthDataResult<RestingHeartRate> {
        return when (val rangeResult = readRestingHeartRateRange(date, date)) {
            is HealthDataResult.Success -> {
                rangeResult.data.firstOrNull()?.let { HealthDataResult.Success(it) }
                    ?: HealthDataResult.Unavailable
            }

            is HealthDataResult.Unavailable -> HealthDataResult.Unavailable
            is HealthDataResult.PermissionDenied -> HealthDataResult.PermissionDenied
            is HealthDataResult.Failure -> HealthDataResult.Failure(rangeResult.error)
        }
    }

    override suspend fun readRestingHeartRateRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): HealthDataResult<List<RestingHeartRate>> {
        val currentClient = client ?: return HealthDataResult.Unavailable
        val zoneId = ZoneId.systemDefault()

        // 1. Native RestingHeartRateRecord is preferred when available.
        val nativeRecordsResult = runCatching {
            val request = ReadRecordsRequest(
                recordType = RestingHeartRateRecord::class,
                timeRangeFilter = timeRangeFilterForCalendarDays(startDate, endDate, zoneId),
            )
            currentClient.readRecords(request).records
        }
        val native = if (nativeRecordsResult.isSuccess) {
            HeartRateDataNormalizer.normalizeRestingHeartRate(nativeRecordsResult.getOrThrow(), zoneId)
        } else {
            emptyList()
        }

        // 2. Primary sleep sessions scope the derived Pulse RHR.
        val sleepResult = readSleepRange(startDate, endDate)
        val sleepSummaries = when (sleepResult) {
            is HealthDataResult.Success -> sleepResult.data
            is HealthDataResult.PermissionDenied,
            is HealthDataResult.Failure,
            is HealthDataResult.Unavailable,
            -> emptyList()
        }

        // 3. Derive Pulse RHR from raw HeartRateRecord samples during primary sleep.
        //    Expand the query window by one day before the requested range because
        //    the primary sleep session for the first requested day can start on the
        //    previous calendar date. Paginate because raw HR can exceed the default
        //    1,000-record page size.
        var rawException: Throwable? = null
        val derived = if (sleepSummaries.isNotEmpty()) {
            val queryStart = startDate.minusDays(1).atStartOfDay(zoneId).toInstant()
            val queryEnd = endDate.plusDays(1).atStartOfDay(zoneId).toInstant()
            val rawRecordsResult = runCatching {
                currentClient.readAllRecords(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(queryStart, queryEnd),
                ).records
            }
            rawException = rawRecordsResult.exceptionOrNull()
            if (rawRecordsResult.isSuccess) {
                HeartRateDataNormalizer.normalizeRestingHeartRate(
                    rawRecordsResult.getOrThrow(),
                    sleepSummaries,
                )
            } else {
                emptyList()
            }
        } else {
            emptyList()
        }

        val combined = HeartRateDataNormalizer.preferNativeRestingHeartRate(native, derived)
            .filter { it.date in startDate..endDate }

        if (combined.isNotEmpty()) {
            val syncedAt = Instant.now()
            healthDataCache.saveRestingHeartRateRange(combined, syncedAt)
            return HealthDataResult.Success(combined)
        }

        // No data was produced. Surface the most relevant error, if any.
        val nativeException = nativeRecordsResult.exceptionOrNull()
        return when {
            sleepResult is HealthDataResult.PermissionDenied -> HealthDataResult.PermissionDenied
            nativeException is SecurityException -> HealthDataResult.PermissionDenied
            rawException is SecurityException -> HealthDataResult.PermissionDenied
            sleepResult is HealthDataResult.Failure -> HealthDataResult.Failure(sleepResult.error)
            nativeException != null -> HealthDataResult.Failure(nativeException)
            rawException != null -> HealthDataResult.Failure(rawException)
            else -> HealthDataResult.Unavailable
        }
    }

    private fun timeRangeFilterForCalendarDays(
        startDate: LocalDate,
        endDate: LocalDate,
        zoneId: ZoneId,
    ): TimeRangeFilter {
        val queryStart = startDate.atStartOfDay(zoneId).toInstant()
        val queryEnd = endDate.plusDays(1).atStartOfDay(zoneId).toInstant()
        return TimeRangeFilter.between(queryStart, queryEnd)
    }

    override suspend fun readHeartRateVariability(
        date: LocalDate,
    ): HealthDataResult<HeartRateVariability> {
        return when (val rangeResult = readHeartRateVariabilityRange(date, date)) {
            is HealthDataResult.Success -> {
                rangeResult.data.firstOrNull()?.let { HealthDataResult.Success(it) }
                    ?: HealthDataResult.Unavailable
            }

            is HealthDataResult.Unavailable -> HealthDataResult.Unavailable
            is HealthDataResult.PermissionDenied -> HealthDataResult.PermissionDenied
            is HealthDataResult.Failure -> HealthDataResult.Failure(rangeResult.error)
        }
    }

    override suspend fun readHeartRateVariabilityRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): HealthDataResult<List<HeartRateVariability>> {
        val currentClient = client ?: return HealthDataResult.Unavailable
        val zoneId = ZoneId.systemDefault()

        // HRV is attributed to the wake date of the primary overnight sleep session.
        // Retrieve sleep first so the normalizer can scope HRV samples to sleep intervals.
        val sleepResult = readSleepRange(startDate, endDate)
        val sleepSummaries = when (sleepResult) {
            is HealthDataResult.Success -> sleepResult.data
            is HealthDataResult.PermissionDenied -> return HealthDataResult.PermissionDenied
            is HealthDataResult.Failure -> return HealthDataResult.Failure(sleepResult.error)
            is HealthDataResult.Unavailable -> emptyList()
        }
        if (sleepSummaries.isEmpty()) {
            return HealthDataResult.Unavailable
        }

        val queryStart = startDate.atStartOfDay(zoneId).toInstant()
        val queryEnd = endDate.plusDays(1).atStartOfDay(zoneId).toInstant()

        return try {
            val request = ReadRecordsRequest(
                recordType = HeartRateVariabilityRmssdRecord::class,
                timeRangeFilter = TimeRangeFilter.between(queryStart, queryEnd),
            )
            val response = currentClient.readRecords(request)
            val summaries = HeartRateDataNormalizer.normalizeHeartRateVariability(
                response.records,
                sleepSummaries,
            ).filter { it.date in startDate..endDate }

            if (summaries.isEmpty()) {
                HealthDataResult.Unavailable
            } else {
                val syncedAt = Instant.now()
                healthDataCache.saveHeartRateVariabilityRange(summaries, syncedAt)
                HealthDataResult.Success(summaries)
            }
        } catch (e: SecurityException) {
            HealthDataResult.PermissionDenied
        } catch (e: Exception) {
            HealthDataResult.Failure(e)
        }
    }

    override suspend fun readHydration(date: LocalDate): HealthDataResult<HydrationSummary> {
        val currentClient = client ?: return HealthDataResult.Unavailable
        val zoneId = ZoneId.systemDefault()

        return try {
            val timeRangeFilter = TimeRangeFilter.between(
                date.atStartOfDay(zoneId).toInstant(),
                date.plusDays(1).atStartOfDay(zoneId).toInstant(),
            )
            val request = AggregateRequest(
                metrics = setOf(HydrationRecord.VOLUME_TOTAL),
                timeRangeFilter = timeRangeFilter,
            )
            val response = currentClient.aggregate(request)
            val summary = HydrationDataNormalizer.toHydrationSummary(response, date)

            if (summary == null) {
                HealthDataResult.Unavailable
            } else {
                val syncedAt = Instant.now()
                healthDataCache.saveHydration(summary, syncedAt)
                HealthDataResult.Success(summary)
            }
        } catch (e: SecurityException) {
            HealthDataResult.PermissionDenied
        } catch (e: Exception) {
            HealthDataResult.Failure(e)
        }
    }

    override suspend fun readHydrationRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): HealthDataResult<List<HydrationSummary>> {
        val currentClient = client ?: return HealthDataResult.Unavailable

        return try {
            val timeRangeFilter = TimeRangeFilter.between(
                startDate.atStartOfDay(),
                endDate.plusDays(1).atStartOfDay(),
            )
            val request = AggregateGroupByPeriodRequest(
                metrics = setOf(HydrationRecord.VOLUME_TOTAL),
                timeRangeFilter = timeRangeFilter,
                timeRangeSlicer = Period.ofDays(1),
            )
            val response = currentClient.aggregateGroupByPeriod(request)
            val summaries = HydrationDataNormalizer.toHydrationSummaries(response)
                .filter { it.date in startDate..endDate }

            if (summaries.isEmpty()) {
                HealthDataResult.Unavailable
            } else {
                val syncedAt = Instant.now()
                healthDataCache.saveHydrationRange(summaries, syncedAt)
                HealthDataResult.Success(summaries)
            }
        } catch (e: SecurityException) {
            HealthDataResult.PermissionDenied
        } catch (e: Exception) {
            HealthDataResult.Failure(e)
        }
    }
}
