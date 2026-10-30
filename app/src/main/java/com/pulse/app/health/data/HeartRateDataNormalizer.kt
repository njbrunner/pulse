package com.pulse.app.health.data

import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import com.pulse.app.health.data.model.HeartRateVariability
import com.pulse.app.health.data.model.RestingHeartRate
import com.pulse.app.health.data.model.SleepSummary
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Normalizes Google Health Connect heart rate records into application-level
 * [RestingHeartRate] and [HeartRateVariability] models.
 *
 * [normalizeRestingHeartRate] groups records by the calendar day on which they
 * occurred and averages values when multiple records exist for the same day.
 *
 * [normalizeHeartRateVariability] attributes HRV to the wake date of the primary
 * overnight sleep session. It averages only the RMSSD records whose timestamps
 * fall within the primary sleep session interval. This matches how Fitbit/Google
 * Health define daily HRV rather than averaging every sample that occurs on a
 * calendar date.
 */
internal object HeartRateDataNormalizer {

    fun normalizeRestingHeartRate(
        records: List<RestingHeartRateRecord>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<RestingHeartRate> {
        return records
            .groupBy { it.toLocalDate(zoneId) }
            .map { (date, dayRecords) ->
                RestingHeartRate(
                    date = date,
                    bpm = averageBpm(dayRecords),
                )
            }
            .sortedBy { it.date }
    }

    fun normalizeHeartRateVariability(
        records: List<HeartRateVariabilityRmssdRecord>,
        sleepSummaries: List<SleepSummary>,
    ): List<HeartRateVariability> {
        if (records.isEmpty() || sleepSummaries.isEmpty()) {
            return emptyList()
        }

        return sleepSummaries
            .mapNotNull { sleep -> normalizeHrvForSleep(records, sleep) }
            .sortedBy { it.date }
    }

    private fun normalizeHrvForSleep(
        records: List<HeartRateVariabilityRmssdRecord>,
        sleep: SleepSummary,
    ): HeartRateVariability? {
        val start = sleep.primarySessionStart ?: return null
        val end = sleep.primarySessionEnd ?: return null

        val sleepRecords = records.filter { it.time in start..end }
        if (sleepRecords.isEmpty()) {
            return null
        }

        return HeartRateVariability(
            date = sleep.date,
            ms = averageMs(sleepRecords),
        )
    }

    private fun averageBpm(records: List<RestingHeartRateRecord>): Int {
        val average = records.sumOf { it.beatsPerMinute }.toDouble() / records.size
        return average.roundToInt()
    }

    private fun averageMs(records: List<HeartRateVariabilityRmssdRecord>): Int {
        val average = records.sumOf { it.heartRateVariabilityMillis } / records.size
        return average.roundToInt()
    }

    private fun RestingHeartRateRecord.toLocalDate(zoneId: ZoneId): LocalDate {
        return time.atZone(zoneOffset ?: zoneId).toLocalDate()
    }
}
