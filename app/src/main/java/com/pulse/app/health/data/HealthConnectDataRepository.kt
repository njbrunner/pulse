package com.pulse.app.health.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
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
import java.time.ZoneId

/**
 * [HealthDataRepository] implementation backed by Google Health Connect.
 *
 * Detailed per-metric retrieval logic is added incrementally per issue. Sleep
 * (#5) and heart rate (#6) are implemented; steps (#7) and hydration (#8) are
 * still placeholders. This class establishes the Health Connect boundary,
 * result mapping, and error-handling pattern.
 */
class HealthConnectDataRepository(
    private val applicationContext: Context,
    private val healthDataCache: HealthDataCache,
    private val healthConnectClient: HealthConnectClient? = null,
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
        return readUnavailable()
    }

    override suspend fun readStepsRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): HealthDataResult<List<StepsSummary>> {
        return readUnavailable()
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

        val queryStart = startDate.atStartOfDay(zoneId).toInstant()
        val queryEnd = endDate.plusDays(1).atStartOfDay(zoneId).toInstant()

        return try {
            val request = ReadRecordsRequest(
                recordType = RestingHeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(queryStart, queryEnd),
            )
            val response = currentClient.readRecords(request)
            val summaries = HeartRateDataNormalizer.normalizeRestingHeartRate(response.records, zoneId)
                .filter { it.date in startDate..endDate }

            if (summaries.isEmpty()) {
                HealthDataResult.Unavailable
            } else {
                val syncedAt = Instant.now()
                healthDataCache.saveRestingHeartRateRange(summaries, syncedAt)
                HealthDataResult.Success(summaries)
            }
        } catch (e: SecurityException) {
            HealthDataResult.PermissionDenied
        } catch (e: Exception) {
            HealthDataResult.Failure(e)
        }
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

        val queryStart = startDate.atStartOfDay(zoneId).toInstant()
        val queryEnd = endDate.plusDays(1).atStartOfDay(zoneId).toInstant()

        return try {
            val request = ReadRecordsRequest(
                recordType = HeartRateVariabilityRmssdRecord::class,
                timeRangeFilter = TimeRangeFilter.between(queryStart, queryEnd),
            )
            val response = currentClient.readRecords(request)
            val summaries = HeartRateDataNormalizer.normalizeHeartRateVariability(response.records, zoneId)
                .filter { it.date in startDate..endDate }

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
        return readUnavailable()
    }

    override suspend fun readHydrationRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): HealthDataResult<List<HydrationSummary>> {
        return readUnavailable()
    }

    private fun <T> readUnavailable(): HealthDataResult<T> {
        return when (client) {
            null -> HealthDataResult.Unavailable
            else -> HealthDataResult.Unavailable
        }
    }
}
