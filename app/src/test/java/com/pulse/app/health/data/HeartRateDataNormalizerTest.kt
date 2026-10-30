package com.pulse.app.health.data

import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class HeartRateDataNormalizerTest {

    private val zoneOffset = ZoneOffset.UTC
    private val date = LocalDate.of(2026, 10, 28)

    @Test
    fun emptyRestingHeartRateRecords_returnsEmptyList() {
        val result = HeartRateDataNormalizer.normalizeRestingHeartRate(emptyList(), zoneOffset)

        assertTrue(result.isEmpty())
    }

    @Test
    fun emptyHeartRateVariabilityRecords_returnsEmptyList() {
        val result = HeartRateDataNormalizer.normalizeHeartRateVariability(emptyList(), zoneOffset)

        assertTrue(result.isEmpty())
    }

    @Test
    fun singleRestingHeartRateRecord_mapsToSummary() {
        val time = date.atStartOfDay(zoneOffset).plusHours(8).toInstant()
        val record = restingHeartRateRecord(time = time, bpm = 58)

        val result = HeartRateDataNormalizer.normalizeRestingHeartRate(listOf(record), zoneOffset)

        assertEquals(1, result.size)
        assertEquals(date, result[0].date)
        assertEquals(58, result[0].bpm)
    }

    @Test
    fun singleHeartRateVariabilityRecord_mapsToSummary() {
        val time = date.atStartOfDay(zoneOffset).plusHours(8).toInstant()
        val record = heartRateVariabilityRecord(time = time, ms = 52.0)

        val result = HeartRateDataNormalizer.normalizeHeartRateVariability(listOf(record), zoneOffset)

        assertEquals(1, result.size)
        assertEquals(date, result[0].date)
        assertEquals(52, result[0].ms)
    }

    @Test
    fun multipleDays_areSortedByDateAndGapsAreOmitted() {
        val day1 = date.minusDays(2)
        val day3 = date
        val records = listOf(
            restingHeartRateRecord(
                time = day3.atStartOfDay(zoneOffset).plusHours(8).toInstant(),
                bpm = 60,
            ),
            restingHeartRateRecord(
                time = day1.atStartOfDay(zoneOffset).plusHours(8).toInstant(),
                bpm = 55,
            ),
        )

        val result = HeartRateDataNormalizer.normalizeRestingHeartRate(records, zoneOffset)

        assertEquals(2, result.size)
        assertEquals(day1, result[0].date)
        assertEquals(55, result[0].bpm)
        assertEquals(day3, result[1].date)
        assertEquals(60, result[1].bpm)
    }

    @Test
    fun multipleRecordsOnSameDay_areAveragedAndRounded() {
        val time = date.atStartOfDay(zoneOffset).plusHours(8).toInstant()
        val records = listOf(
            restingHeartRateRecord(time = time, bpm = 56),
            restingHeartRateRecord(time = time.plusSeconds(300), bpm = 60),
        )

        val result = HeartRateDataNormalizer.normalizeRestingHeartRate(records, zoneOffset)

        assertEquals(1, result.size)
        assertEquals(58, result[0].bpm)
    }

    @Test
    fun multipleHrvRecordsOnSameDay_areAveragedAndRounded() {
        val time = date.atStartOfDay(zoneOffset).plusHours(8).toInstant()
        val records = listOf(
            heartRateVariabilityRecord(time = time, ms = 50.4),
            heartRateVariabilityRecord(time = time.plusSeconds(300), ms = 52.4),
        )

        val result = HeartRateDataNormalizer.normalizeHeartRateVariability(records, zoneOffset)

        assertEquals(1, result.size)
        assertEquals(51, result[0].ms)
    }

    @Test
    fun recordWithZoneOffset_isAttributedToCorrectLocalDate() {
        // 01:00 UTC on Oct 29 is 20:00 Oct 28 in UTC-5
        val lateUtcTime = date.plusDays(1).atStartOfDay(zoneOffset).plusHours(1).toInstant()
        val offset = ZoneOffset.ofHours(-5)
        val record = restingHeartRateRecord(
            time = lateUtcTime,
            zoneOffset = offset,
            bpm = 58,
        )

        val result = HeartRateDataNormalizer.normalizeRestingHeartRate(listOf(record), zoneOffset)

        assertEquals(1, result.size)
        assertEquals(date, result[0].date)
    }

    @Test
    fun recordWithNullZoneOffset_usesProvidedZoneId() {
        val localTime = date.atStartOfDay(zoneOffset).plusHours(8).toInstant()
        val record = RestingHeartRateRecord(
            time = localTime,
            zoneOffset = null,
            beatsPerMinute = 58,
            metadata = metadata(),
        )

        val result = HeartRateDataNormalizer.normalizeRestingHeartRate(listOf(record), zoneOffset)

        assertEquals(1, result.size)
        assertEquals(date, result[0].date)
    }

    private fun restingHeartRateRecord(
        time: Instant,
        zoneOffset: ZoneOffset = this.zoneOffset,
        bpm: Long,
    ): RestingHeartRateRecord {
        return RestingHeartRateRecord(
            time = time,
            zoneOffset = zoneOffset,
            beatsPerMinute = bpm,
            metadata = metadata(),
        )
    }

    private fun heartRateVariabilityRecord(
        time: Instant,
        zoneOffset: ZoneOffset = this.zoneOffset,
        ms: Double,
    ): HeartRateVariabilityRmssdRecord {
        return HeartRateVariabilityRmssdRecord(
            time = time,
            zoneOffset = zoneOffset,
            heartRateVariabilityMillis = ms,
            metadata = metadata(),
        )
    }

    private fun metadata(): Metadata {
        return Metadata.activelyRecorded(
            device = Device(type = Device.TYPE_UNKNOWN),
        )
    }
}
