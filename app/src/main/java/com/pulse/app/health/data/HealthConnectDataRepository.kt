package com.pulse.app.health.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import com.pulse.app.health.data.model.HeartRateVariability
import com.pulse.app.health.data.model.HydrationSummary
import com.pulse.app.health.data.model.RestingHeartRate
import com.pulse.app.health.data.model.SleepSummary
import com.pulse.app.health.data.model.StepsSummary
import java.time.LocalDate

/**
 * [HealthDataRepository] implementation backed by Google Health Connect.
 *
 * Detailed per-metric retrieval logic will be implemented in later issues
 * (#5 sleep, #6 heart rate, #7 steps, #8 hydration). This class establishes
 * the Health Connect boundary, result mapping, and error-handling pattern.
 */
class HealthConnectDataRepository(
    private val applicationContext: Context,
    private val healthConnectClient: HealthConnectClient? = null,
) : HealthDataRepository {

    private val client: HealthConnectClient? by lazy {
        healthConnectClient ?: when (HealthConnectClient.getSdkStatus(applicationContext)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectClient.getOrCreate(applicationContext)
            else -> null
        }
    }

    override suspend fun readSleep(date: LocalDate): HealthDataResult<SleepSummary> {
        return readUnavailable()
    }

    override suspend fun readSleepRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): HealthDataResult<List<SleepSummary>> {
        return readUnavailable()
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
        return readUnavailable()
    }

    override suspend fun readRestingHeartRateRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): HealthDataResult<List<RestingHeartRate>> {
        return readUnavailable()
    }

    override suspend fun readHeartRateVariability(
        date: LocalDate,
    ): HealthDataResult<HeartRateVariability> {
        return readUnavailable()
    }

    override suspend fun readHeartRateVariabilityRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): HealthDataResult<List<HeartRateVariability>> {
        return readUnavailable()
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
