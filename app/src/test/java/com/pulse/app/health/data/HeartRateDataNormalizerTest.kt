package com.pulse.app.health.data

import androidx.health.connect.client.records.HeartRateRecord
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
    fun emptyHeartRateRecordsOrSleepSummaries_returnsEmptyList() {
        val sleep = sleepSummary(date)
        val result = HeartRateDataNormalizer.normalizeRestingHeartRate(
            records = emptyList<HeartRateRecord>(),
            sleepSummaries = listOf(sleep),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun derivedRhr_singleValidTenMinuteWindow_returnsWindowMeanRounded() {
        val sleepStart = date.atTime(22, 0).toInstant(zoneOffset)
        val sleepEnd = date.plusDays(1).atTime(6, 0).toInstant(zoneOffset)
        val sleep = sleepSummary(date, sleepStart, sleepEnd)

        val record = heartRateRecord(
            start = sleepStart,
            end = sleepEnd,
            samples = listOf(
                sample(time = sleepStart.plusSeconds(0), bpm = 50),
                sample(time = sleepStart.plusSeconds(120), bpm = 51),
                sample(time = sleepStart.plusSeconds(240), bpm = 52),
                sample(time = sleepStart.plusSeconds(360), bpm = 53),
                sample(time = sleepStart.plusSeconds(480), bpm = 54),
            ),
        )

        val result = HeartRateDataNormalizer.normalizeRestingHeartRate(
            records = listOf(record),
            sleepSummaries = listOf(sleep),
        )

        assertEquals(1, result.size)
        assertEquals(date, result[0].date)
        // Mean = (50 + 51 + 52 + 53 + 54) / 5 = 52.0
        assertEquals(52, result[0].bpm)
    }

    @Test
    fun derivedRhr_multipleQualifyingWindows_lowestMeanWins() {
        val sleepStart = date.atTime(22, 0).toInstant(zoneOffset)
        val sleepEnd = date.plusDays(1).atTime(6, 0).toInstant(zoneOffset)
        val sleep = sleepSummary(date, sleepStart, sleepEnd)

        val record = heartRateRecord(
            start = sleepStart,
            end = sleepEnd,
            samples = listOf(
                sample(time = sleepStart.plusSeconds(0), bpm = 60),
                sample(time = sleepStart.plusSeconds(120), bpm = 60),
                sample(time = sleepStart.plusSeconds(240), bpm = 60),
                sample(time = sleepStart.plusSeconds(360), bpm = 60),
                sample(time = sleepStart.plusSeconds(480), bpm = 60),
                sample(time = sleepStart.plusSeconds(600), bpm = 50),
                sample(time = sleepStart.plusSeconds(720), bpm = 50),
                sample(time = sleepStart.plusSeconds(840), bpm = 50),
                sample(time = sleepStart.plusSeconds(960), bpm = 50),
                sample(time = sleepStart.plusSeconds(1080), bpm = 50),
            ),
        )

        val result = HeartRateDataNormalizer.normalizeRestingHeartRate(
            records = listOf(record),
            sleepSummaries = listOf(sleep),
        )

        assertEquals(1, result.size)
        assertEquals(50, result[0].bpm)
    }

    @Test
    fun derivedRhr_meanRoundsToNearestWholeBpm() {
        val sleepStart = date.atTime(22, 0).toInstant(zoneOffset)
        val sleepEnd = date.plusDays(1).atTime(6, 0).toInstant(zoneOffset)
        val sleep = sleepSummary(date, sleepStart, sleepEnd)

        val record = heartRateRecord(
            start = sleepStart,
            end = sleepEnd,
            samples = listOf(
                sample(time = sleepStart.plusSeconds(0), bpm = 49),
                sample(time = sleepStart.plusSeconds(120), bpm = 50),
                sample(time = sleepStart.plusSeconds(240), bpm = 51),
                sample(time = sleepStart.plusSeconds(360), bpm = 52),
                sample(time = sleepStart.plusSeconds(480), bpm = 52),
            ),
        )

        val result = HeartRateDataNormalizer.normalizeRestingHeartRate(
            records = listOf(record),
            sleepSummaries = listOf(sleep),
        )

        // Mean = 50.8 -> rounds to 51
        assertEquals(51, result[0].bpm)
    }

    @Test
    fun derivedRhr_samplesOutsidePrimarySleepInterval_areIgnored() {
        val sleepStart = date.atTime(22, 0).toInstant(zoneOffset)
        val sleepEnd = date.plusDays(1).atTime(6, 0).toInstant(zoneOffset)
        val sleep = sleepSummary(date, sleepStart, sleepEnd)

        val record = heartRateRecord(
            start = sleepStart.minusSeconds(600),
            end = sleepEnd.plusSeconds(600),
            samples = listOf(
                sample(time = sleepStart.minusSeconds(300), bpm = 30),
                sample(time = sleepStart.plusSeconds(60), bpm = 50),
                sample(time = sleepStart.plusSeconds(180), bpm = 51),
                sample(time = sleepStart.plusSeconds(300), bpm = 52),
                sample(time = sleepStart.plusSeconds(420), bpm = 53),
                sample(time = sleepStart.plusSeconds(540), bpm = 54),
                sample(time = sleepEnd.plusSeconds(300), bpm = 30),
            ),
        )

        val result = HeartRateDataNormalizer.normalizeRestingHeartRate(
            records = listOf(record),
            sleepSummaries = listOf(sleep),
        )

        assertEquals(1, result.size)
        assertEquals(52, result[0].bpm)
    }

    @Test
    fun derivedRhr_noPrimarySleepSession_returnsEmptyList() {
        val sleepStart = date.atTime(22, 0).toInstant(zoneOffset)
        val sleepEnd = date.plusDays(1).atTime(6, 0).toInstant(zoneOffset)
        val sleepWithoutInterval = SleepSummary(
            date = date,
            duration = Duration.ofHours(8),
        )

        val record = heartRateRecord(
            start = sleepStart,
            end = sleepEnd,
            samples = listOf(
                sample(time = sleepStart.plusSeconds(60), bpm = 50),
                sample(time = sleepStart.plusSeconds(120), bpm = 51),
                sample(time = sleepStart.plusSeconds(240), bpm = 52),
                sample(time = sleepStart.plusSeconds(360), bpm = 53),
                sample(time = sleepStart.plusSeconds(480), bpm = 54),
            ),
        )

        val result = HeartRateDataNormalizer.normalizeRestingHeartRate(
            records = listOf(record),
            sleepSummaries = listOf(sleepWithoutInterval),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun derivedRhr_insufficientWindowCoverage_returnsEmptyList() {
        val sleepStart = date.atTime(22, 0).toInstant(zoneOffset)
        val sleepEnd = date.plusDays(1).atTime(6, 0).toInstant(zoneOffset)
        val sleep = sleepSummary(date, sleepStart, sleepEnd)

        // Five samples clustered in the first 90 seconds: not enough span.
        val record = heartRateRecord(
            start = sleepStart,
            end = sleepEnd,
            samples = listOf(
                sample(time = sleepStart.plusSeconds(0), bpm = 50),
                sample(time = sleepStart.plusSeconds(15), bpm = 50),
                sample(time = sleepStart.plusSeconds(30), bpm = 50),
                sample(time = sleepStart.plusSeconds(60), bpm = 50),
                sample(time = sleepStart.plusSeconds(90), bpm = 50),
            ),
        )

        val result = HeartRateDataNormalizer.normalizeRestingHeartRate(
            records = listOf(record),
            sleepSummaries = listOf(sleep),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun derivedRhr_sparseClusteredSamples_doNotQualify() {
        val sleepStart = date.atTime(22, 0).toInstant(zoneOffset)
        val sleepEnd = date.plusDays(1).atTime(6, 0).toInstant(zoneOffset)
        val sleep = sleepSummary(date, sleepStart, sleepEnd)

        // Five samples clustered in the first two minutes, plus one sample
        // 13 minutes later. Any 10-minute window containing the cluster has a
        // span of only two minutes, and any window containing the later sample
        // lacks five observations, so no window qualifies.
        val record = heartRateRecord(
            start = sleepStart,
            end = sleepEnd,
            samples = listOf(
                sample(time = sleepStart.plusSeconds(0), bpm = 40),
                sample(time = sleepStart.plusSeconds(30), bpm = 40),
                sample(time = sleepStart.plusSeconds(60), bpm = 40),
                sample(time = sleepStart.plusSeconds(90), bpm = 40),
                sample(time = sleepStart.plusSeconds(120), bpm = 40),
                sample(time = sleepStart.plusSeconds(780), bpm = 100),
            ),
        )

        val result = HeartRateDataNormalizer.normalizeRestingHeartRate(
            records = listOf(record),
            sleepSummaries = listOf(sleep),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun derivedRhr_samplesDistributedAcrossWindow_qualify() {
        val sleepStart = date.atTime(22, 0).toInstant(zoneOffset)
        val sleepEnd = date.plusDays(1).atTime(6, 0).toInstant(zoneOffset)
        val sleep = sleepSummary(date, sleepStart, sleepEnd)

        // Five samples spanning exactly 8 minutes qualify the window.
        val record = heartRateRecord(
            start = sleepStart,
            end = sleepEnd,
            samples = listOf(
                sample(time = sleepStart.plusSeconds(0), bpm = 55),
                sample(time = sleepStart.plusSeconds(120), bpm = 55),
                sample(time = sleepStart.plusSeconds(240), bpm = 55),
                sample(time = sleepStart.plusSeconds(360), bpm = 55),
                sample(time = sleepStart.plusSeconds(480), bpm = 55),
            ),
        )

        val result = HeartRateDataNormalizer.normalizeRestingHeartRate(
            records = listOf(record),
            sleepSummaries = listOf(sleep),
        )

        assertEquals(1, result.size)
        assertEquals(55, result[0].bpm)
    }

    @Test
    fun derivedRhr_overnightSleepCrossingMidnight_isAttributedToWakeDate() {
        val wakeDate = date
        val sleepStart = wakeDate.minusDays(1).atTime(22, 0).toInstant(zoneOffset)
        val sleepEnd = wakeDate.atTime(6, 0).toInstant(zoneOffset)
        val sleep = sleepSummary(wakeDate, sleepStart, sleepEnd)

        val record = heartRateRecord(
            start = sleepStart,
            end = sleepEnd,
            samples = listOf(
                sample(time = sleepStart.plusSeconds(60), bpm = 48),
                sample(time = sleepStart.plusSeconds(180), bpm = 48),
                sample(time = sleepStart.plusSeconds(300), bpm = 48),
                sample(time = sleepStart.plusSeconds(420), bpm = 48),
                sample(time = sleepStart.plusSeconds(540), bpm = 48),
            ),
        )

        val result = HeartRateDataNormalizer.normalizeRestingHeartRate(
            records = listOf(record),
            sleepSummaries = listOf(sleep),
        )

        assertEquals(1, result.size)
        assertEquals(wakeDate, result[0].date)
        assertEquals(48, result[0].bpm)
    }

    @Test
    fun derivedRhr_multipleDays_returnsOneValuePerDayAndOmitsMissingDay() {
        val day1 = date
        val day1SleepStart = day1.minusDays(1).atTime(22, 0).toInstant(zoneOffset)
        val day1SleepEnd = day1.atTime(6, 0).toInstant(zoneOffset)
        val day1Sleep = sleepSummary(day1, day1SleepStart, day1SleepEnd)

        val day2 = date.plusDays(1)
        val day2SleepStart = day2.minusDays(1).atTime(22, 0).toInstant(zoneOffset)
        val day2SleepEnd = day2.atTime(6, 0).toInstant(zoneOffset)
        val day2Sleep = sleepSummary(day2, day2SleepStart, day2SleepEnd)

        // No sleep for day3.
        val day3 = date.plusDays(2)

        val record = heartRateRecord(
            start = day1SleepStart,
            end = day2SleepEnd,
            samples = listOf(
                sample(time = day1SleepStart.plusSeconds(60), bpm = 50),
                sample(time = day1SleepStart.plusSeconds(180), bpm = 50),
                sample(time = day1SleepStart.plusSeconds(300), bpm = 50),
                sample(time = day1SleepStart.plusSeconds(420), bpm = 50),
                sample(time = day1SleepStart.plusSeconds(540), bpm = 50),
                sample(time = day2SleepStart.plusSeconds(60), bpm = 55),
                sample(time = day2SleepStart.plusSeconds(180), bpm = 55),
                sample(time = day2SleepStart.plusSeconds(300), bpm = 55),
                sample(time = day2SleepStart.plusSeconds(420), bpm = 55),
                sample(time = day2SleepStart.plusSeconds(540), bpm = 55),
            ),
        )

        val result = HeartRateDataNormalizer.normalizeRestingHeartRate(
            records = listOf(record),
            sleepSummaries = listOf(day1Sleep, day2Sleep),
        )

        assertEquals(2, result.size)
        assertEquals(day1, result[0].date)
        assertEquals(50, result[0].bpm)
        assertEquals(day2, result[1].date)
        assertEquals(55, result[1].bpm)
        assertTrue(result.none { it.date == day3 })
    }

    @Test
    fun preferNativeRestingHeartRate_nativeAvailableForDate_usesNative() {
        val native = listOf(
            com.pulse.app.health.data.model.RestingHeartRate(date = date, bpm = 60),
        )
        val derived = listOf(
            com.pulse.app.health.data.model.RestingHeartRate(date = date, bpm = 55),
        )

        val result = HeartRateDataNormalizer.preferNativeRestingHeartRate(native, derived)

        assertEquals(1, result.size)
        assertEquals(60, result[0].bpm)
    }

    @Test
    fun preferNativeRestingHeartRate_nativeMissingForDate_usesDerived() {
        val native = listOf(
            com.pulse.app.health.data.model.RestingHeartRate(date = date.minusDays(1), bpm = 60),
        )
        val derived = listOf(
            com.pulse.app.health.data.model.RestingHeartRate(date = date, bpm = 55),
        )

        val result = HeartRateDataNormalizer.preferNativeRestingHeartRate(native, derived)

        assertEquals(2, result.size)
        assertEquals(date.minusDays(1), result[0].date)
        assertEquals(60, result[0].bpm)
        assertEquals(date, result[1].date)
        assertEquals(55, result[1].bpm)
    }

    @Test
    fun preferNativeRestingHeartRate_onlyNative_returnsNative() {
        val native = listOf(
            com.pulse.app.health.data.model.RestingHeartRate(date = date, bpm = 60),
        )

        val result = HeartRateDataNormalizer.preferNativeRestingHeartRate(native, emptyList())

        assertEquals(1, result.size)
        assertEquals(60, result[0].bpm)
    }

    @Test
    fun preferNativeRestingHeartRate_onlyDerived_returnsDerived() {
        val derived = listOf(
            com.pulse.app.health.data.model.RestingHeartRate(date = date, bpm = 55),
        )

        val result = HeartRateDataNormalizer.preferNativeRestingHeartRate(emptyList(), derived)

        assertEquals(1, result.size)
        assertEquals(55, result[0].bpm)
    }

    @Test
    fun preferNativeRestingHeartRate_emptyInputs_returnsEmptyList() {
        val result = HeartRateDataNormalizer.preferNativeRestingHeartRate(emptyList(), emptyList())

        assertTrue(result.isEmpty())
    }

    @Test
    fun derivedRhr_validBpmBoundaries_areIncluded() {
        val sleepStart = date.atTime(22, 0).toInstant(zoneOffset)
        val sleepEnd = date.plusDays(1).atTime(6, 0).toInstant(zoneOffset)
        val sleep = sleepSummary(date, sleepStart, sleepEnd)

        val record = heartRateRecord(
            start = sleepStart,
            end = sleepEnd,
            samples = listOf(
                sample(time = sleepStart.plusSeconds(0), bpm = 1),
                sample(time = sleepStart.plusSeconds(120), bpm = 50),
                sample(time = sleepStart.plusSeconds(240), bpm = 51),
                sample(time = sleepStart.plusSeconds(360), bpm = 52),
                sample(time = sleepStart.plusSeconds(480), bpm = 300),
            ),
        )

        val result = HeartRateDataNormalizer.normalizeRestingHeartRate(
            records = listOf(record),
            sleepSummaries = listOf(sleep),
        )

        assertEquals(1, result.size)
        // Mean = (1 + 50 + 51 + 52 + 300) / 5 = 90.8 -> 91
        assertEquals(91, result[0].bpm)
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

    private fun heartRateRecord(
        start: Instant,
        end: Instant,
        samples: List<HeartRateRecord.Sample>,
    ): HeartRateRecord {
        return HeartRateRecord(
            startTime = start,
            startZoneOffset = zoneOffset,
            endTime = end,
            endZoneOffset = zoneOffset,
            samples = samples,
            metadata = metadata(),
        )
    }

    private fun sample(time: Instant, bpm: Long): HeartRateRecord.Sample {
        return HeartRateRecord.Sample(
            time = time,
            beatsPerMinute = bpm,
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
