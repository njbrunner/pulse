package com.pulse.app.health.data

import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import com.pulse.app.health.data.model.HeartRateVariability
import com.pulse.app.health.data.model.RestingHeartRate
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Normalizes Google Health Connect heart rate records into application-level
 * [RestingHeartRate] and [HeartRateVariability] models.
 *
 * Records are grouped by the calendar day on which they occurred. When more than
 * one record exists for a day, the values are averaged. Days with no records are
 * omitted, so missing data is represented as unavailable rather than zero.
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
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<HeartRateVariability> {
        return records
            .groupBy { it.toLocalDate(zoneId) }
            .map { (date, dayRecords) ->
                HeartRateVariability(
                    date = date,
                    ms = averageMs(dayRecords),
                )
            }
            .sortedBy { it.date }
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

    private fun HeartRateVariabilityRmssdRecord.toLocalDate(zoneId: ZoneId): LocalDate {
        return time.atZone(zoneOffset ?: zoneId).toLocalDate()
    }
}
