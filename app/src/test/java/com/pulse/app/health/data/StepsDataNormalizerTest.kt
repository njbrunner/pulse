package com.pulse.app.health.data

import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class StepsDataNormalizerTest {

    private val zoneOffset = ZoneOffset.UTC
    private val date = LocalDate.of(2026, 10, 28)

    @Test
    fun emptyRecords_returnsEmptyList() {
        val result = StepsDataNormalizer.normalize(emptyList(), zoneOffset)

        assertTrue(result.isEmpty())
    }

    @Test
    fun singleRecordWithinOneDay_mapsToSummary() {
        val start = date.atTime(8, 0).toInstant(zoneOffset)
        val end = date.atTime(9, 0).toInstant(zoneOffset)
        val record = stepsRecord(start = start, end = end, count = 1_000L)

        val result = StepsDataNormalizer.normalize(listOf(record), zoneOffset)

        assertEquals(1, result.size)
        assertEquals(date, result[0].date)
        assertEquals(1_000L, result[0].steps)
    }

    @Test
    fun multipleRecordsOnSameDay_areSummed() {
        val records = listOf(
            stepsRecord(
                start = date.atTime(8, 0).toInstant(zoneOffset),
                end = date.atTime(9, 0).toInstant(zoneOffset),
                count = 1_000L,
            ),
            stepsRecord(
                start = date.atTime(10, 0).toInstant(zoneOffset),
                end = date.atTime(11, 0).toInstant(zoneOffset),
                count = 2_500L,
            ),
        )

        val result = StepsDataNormalizer.normalize(records, zoneOffset)

        assertEquals(1, result.size)
        assertEquals(date, result[0].date)
        assertEquals(3_500L, result[0].steps)
    }

    @Test
    fun multipleDays_areSortedByDate() {
        val day1 = date
        val day2 = date.plusDays(1)
        val records = listOf(
            stepsRecord(
                start = day2.atTime(8, 0).toInstant(zoneOffset),
                end = day2.atTime(9, 0).toInstant(zoneOffset),
                count = 2_000L,
            ),
            stepsRecord(
                start = day1.atTime(8, 0).toInstant(zoneOffset),
                end = day1.atTime(9, 0).toInstant(zoneOffset),
                count = 1_000L,
            ),
        )

        val result = StepsDataNormalizer.normalize(records, zoneOffset)

        assertEquals(2, result.size)
        assertEquals(day1, result[0].date)
        assertEquals(1_000L, result[0].steps)
        assertEquals(day2, result[1].date)
        assertEquals(2_000L, result[1].steps)
    }

    @Test
    fun recordCrossingMidnight_isSplitProportionallyByDay() {
        val previousDay = date.minusDays(1)
        val start = previousDay.atTime(23, 0).toInstant(zoneOffset)
        val end = date.atTime(1, 0).toInstant(zoneOffset)
        val record = stepsRecord(start = start, end = end, count = 1_200L)

        val result = StepsDataNormalizer.normalize(listOf(record), zoneOffset)

        assertEquals(2, result.size)
        assertEquals(previousDay, result[0].date)
        assertEquals(600L, result[0].steps)
        assertEquals(date, result[1].date)
        assertEquals(600L, result[1].steps)
    }

    @Test
    fun recordCrossingMidnight_preservesTotalStepCount() {
        val previousDay = date.minusDays(1)
        val start = previousDay.atTime(23, 30).toInstant(zoneOffset)
        val end = date.atTime(0, 30).toInstant(zoneOffset)
        val record = stepsRecord(start = start, end = end, count = 1L)

        val result = StepsDataNormalizer.normalize(listOf(record), zoneOffset)

        val totalSteps = result.sumOf { it.steps }
        assertEquals(1L, totalSteps)
    }

    @Test
    fun daysWithNoRecords_areOmitted() {
        val record = stepsRecord(
            start = date.atTime(8, 0).toInstant(zoneOffset),
            end = date.atTime(9, 0).toInstant(zoneOffset),
            count = 1_000L,
        )

        val result = StepsDataNormalizer.normalize(listOf(record), zoneOffset)

        assertEquals(1, result.size)
        assertEquals(date, result[0].date)
    }

    @Test
    fun recordedZeroCount_isIncludedAsZero() {
        val record = stepsRecord(
            start = date.atTime(8, 0).toInstant(zoneOffset),
            end = date.atTime(9, 0).toInstant(zoneOffset),
            count = 0L,
        )

        val result = StepsDataNormalizer.normalize(listOf(record), zoneOffset)

        assertEquals(1, result.size)
        assertEquals(date, result[0].date)
        assertEquals(0L, result[0].steps)
    }

    @Test
    fun recordWithZeroDuration_isIgnored() {
        val time = date.atTime(8, 0).toInstant(zoneOffset)
        val record = stepsRecord(start = time, end = time, count = 1_000L)

        val result = StepsDataNormalizer.normalize(listOf(record), zoneOffset)

        assertTrue(result.isEmpty())
    }

    @Test
    fun overlappingRecords_areSummed() {
        val records = listOf(
            stepsRecord(
                start = date.atTime(8, 0).toInstant(zoneOffset),
                end = date.atTime(9, 0).toInstant(zoneOffset),
                count = 1_000L,
            ),
            stepsRecord(
                start = date.atTime(8, 30).toInstant(zoneOffset),
                end = date.atTime(9, 30).toInstant(zoneOffset),
                count = 500L,
            ),
        )

        val result = StepsDataNormalizer.normalize(records, zoneOffset)

        assertEquals(1, result.size)
        assertEquals(1_500L, result[0].steps)
    }

    private fun stepsRecord(
        start: Instant,
        end: Instant,
        count: Long,
    ): StepsRecord {
        return StepsRecord(
            startTime = start,
            startZoneOffset = zoneOffset,
            endTime = end,
            endZoneOffset = zoneOffset,
            count = count,
            metadata = Metadata.activelyRecorded(
                device = Device(type = Device.TYPE_UNKNOWN),
            ),
        )
    }
}
