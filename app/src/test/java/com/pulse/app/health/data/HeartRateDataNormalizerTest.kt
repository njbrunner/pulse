package com.pulse.app.health.data

import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import com.pulse.app.health.data.model.SleepSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
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
    fun singleRestingHeartRateRecord_mapsToSummary() {
        val time = date.atStartOfDay(zoneOffset).plusHours(8).toInstant()
        val record = restingHeartRateRecord(time = time, bpm = 58)

        val result = HeartRateDataNormalizer.normalizeRestingHeartRate(listOf(record), zoneOffset)

        assertEquals(1, result.size)
        assertEquals(date, result[0].date)
        assertEquals(58, result[0].bpm)
    }

    @Test
    fun multipleRestingHeartRateRecordsOnSameDay_areAveragedAndRounded() {
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
    fun emptyHrvRecords_returnsEmptyList() {
        val result = HeartRateDataNormalizer.normalizeHeartRateVariability(
            emptyList(),
            listOf(sleepSummary(date)),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun emptySleepSummaries_returnsEmptyList() {
        val time = date.atStartOfDay(zoneOffset).plusHours(8).toInstant()
        val record = heartRateVariabilityRecord(time = time, ms = 52.0)

        val result = HeartRateDataNormalizer.normalizeHeartRateVariability(
            listOf(record),
            emptyList(),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun hrvRecordsWithinPrimarySleepInterval_areAveragedAndAttributedToWakeDate() {
        val sleepStart = date.minusDays(1).atTime(22, 0).toInstant(zoneOffset)
        val sleepEnd = date.atTime(6, 0).toInstant(zoneOffset)
        val sleep = sleepSummary(date, sleepStart, sleepEnd)

        val records = listOf(
            heartRateVariabilityRecord(time = sleepStart.plusSeconds(300), ms = 50.0),
            heartRateVariabilityRecord(time = sleepEnd.minusSeconds(300), ms = 54.0),
        )

        val result = HeartRateDataNormalizer.normalizeHeartRateVariability(records, listOf(sleep))

        assertEquals(1, result.size)
        assertEquals(date, result[0].date)
        assertEquals(52, result[0].ms)
    }

    @Test
    fun hrvRecordsOutsidePrimarySleepInterval_areIgnored() {
        val sleepStart = date.minusDays(1).atTime(22, 0).toInstant(zoneOffset)
        val sleepEnd = date.atTime(6, 0).toInstant(zoneOffset)
        val sleep = sleepSummary(date, sleepStart, sleepEnd)

        val daytimeRecord = heartRateVariabilityRecord(
            time = date.atTime(14, 0).toInstant(zoneOffset),
            ms = 40.0,
        )

        val result = HeartRateDataNormalizer.normalizeHeartRateVariability(
            listOf(daytimeRecord),
            listOf(sleep),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun multipleSleepSessions_eachPrimarySessionContributesItsOwnHrv() {
        val day1 = date.minusDays(1)
        val day1SleepStart = day1.atTime(22, 0).toInstant(zoneOffset)
        val day1SleepEnd = date.atTime(6, 0).toInstant(zoneOffset)

        val day2SleepStart = date.atTime(22, 0).toInstant(zoneOffset)
        val day2SleepEnd = date.plusDays(1).atTime(6, 0).toInstant(zoneOffset)

        val sleeps = listOf(
            sleepSummary(day1, day1SleepStart, day1SleepEnd),
            sleepSummary(date, day2SleepStart, day2SleepEnd),
        )

        val records = listOf(
            heartRateVariabilityRecord(time = day1SleepStart.plusSeconds(300), ms = 50.0),
            heartRateVariabilityRecord(time = day2SleepStart.plusSeconds(300), ms = 60.0),
        )

        val result = HeartRateDataNormalizer.normalizeHeartRateVariability(records, sleeps)

        assertEquals(2, result.size)
        assertEquals(day1, result[0].date)
        assertEquals(50, result[0].ms)
        assertEquals(date, result[1].date)
        assertEquals(60, result[1].ms)
    }

    @Test
    fun missingPrimarySessionInterval_returnsEmptyList() {
        val sleep = SleepSummary(date, Duration.ofHours(8))
        val record = heartRateVariabilityRecord(
            time = date.atStartOfDay(zoneOffset).plusHours(2).toInstant(),
            ms = 50.0,
        )

        val result = HeartRateDataNormalizer.normalizeHeartRateVariability(
            listOf(record),
            listOf(sleep),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun noHrvInPrimarySleepInterval_returnsEmptyList() {
        val sleepStart = date.minusDays(1).atTime(22, 0).toInstant(zoneOffset)
        val sleepEnd = date.atTime(6, 0).toInstant(zoneOffset)
        val sleep = sleepSummary(date, sleepStart, sleepEnd)

        val result = HeartRateDataNormalizer.normalizeHeartRateVariability(emptyList(), listOf(sleep))

        assertTrue(result.isEmpty())
    }

    @Test
    fun multipleHrvRecordsWithinSameSleepInterval_areAveragedAndRounded() {
        val sleepStart = date.minusDays(1).atTime(22, 0).toInstant(zoneOffset)
        val sleepEnd = date.atTime(6, 0).toInstant(zoneOffset)
        val sleep = sleepSummary(date, sleepStart, sleepEnd)

        val records = listOf(
            heartRateVariabilityRecord(time = sleepStart.plusSeconds(300), ms = 50.4),
            heartRateVariabilityRecord(time = sleepEnd.minusSeconds(300), ms = 52.4),
        )

        val result = HeartRateDataNormalizer.normalizeHeartRateVariability(records, listOf(sleep))

        assertEquals(1, result.size)
        assertEquals(date, result[0].date)
        assertEquals(51, result[0].ms)
    }

    @Test
    fun hrvRecordExactlyAtSessionBoundaries_isIncluded() {
        val sleepStart = date.minusDays(1).atTime(22, 0).toInstant(zoneOffset)
        val sleepEnd = date.atTime(6, 0).toInstant(zoneOffset)
        val sleep = sleepSummary(date, sleepStart, sleepEnd)

        val records = listOf(
            heartRateVariabilityRecord(time = sleepStart, ms = 50.0),
            heartRateVariabilityRecord(time = sleepEnd, ms = 54.0),
        )

        val result = HeartRateDataNormalizer.normalizeHeartRateVariability(records, listOf(sleep))

        assertEquals(1, result.size)
        assertEquals(52, result[0].ms)
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

    private fun sleepSummary(
        date: LocalDate,
        primarySessionStart: Instant? = null,
        primarySessionEnd: Instant? = null,
    ): SleepSummary {
        return SleepSummary(
            date = date,
            duration = Duration.ofHours(8),
            primarySessionStart = primarySessionStart,
            primarySessionEnd = primarySessionEnd,
        )
    }

    private fun metadata(): Metadata {
        return Metadata.activelyRecorded(
            device = Device(type = Device.TYPE_UNKNOWN),
        )
    }
}
