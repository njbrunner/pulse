package com.pulse.app.health.data

import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class SleepDataNormalizerTest {

    private val zoneOffset = ZoneOffset.UTC
    private val date = LocalDate.of(2026, 10, 28)

    @Test
    fun singleSleepSession_computesDurationEfficiencyAndContinuity() {
        val start = date.atStartOfDay(zoneOffset).minusHours(2).toInstant()
        val end = date.atStartOfDay(zoneOffset).plusHours(6).toInstant()
        val session = sleepSession(
            start = start,
            end = end,
            stages = listOf(sleepStage(start, end)),
        )

        val result = SleepDataNormalizer.normalize(listOf(session), zoneOffset)

        assertEquals(1, result.size)
        assertEquals(date, result[0].date)
        assertEquals(Duration.ofHours(8), result[0].duration)
        assertEquals(1.0f, result[0].efficiency)
        assertEquals(1.0f, result[0].continuity)
    }

    @Test
    fun sessionCrossingMidnight_isAttributedToWakeDate() {
        val previousDay = date.minusDays(1)
        val start = previousDay.atTime(23, 0).toInstant(zoneOffset)
        val end = date.atTime(7, 0).toInstant(zoneOffset)
        val session = sleepSession(
            start = start,
            end = end,
            stages = listOf(sleepStage(start, end)),
        )

        val result = SleepDataNormalizer.normalize(listOf(session), zoneOffset)

        assertEquals(1, result.size)
        assertEquals(date, result[0].date)
        assertEquals(Duration.ofHours(8), result[0].duration)
    }

    @Test
    fun sessionSpanningMidnightBoundary_isAttributedToWakeDate() {
        val previousDay = date.minusDays(1)
        val start = previousDay.atTime(23, 59).toInstant(zoneOffset)
        val end = date.atTime(0, 1).toInstant(zoneOffset)
        val session = sleepSession(
            start = start,
            end = end,
            stages = listOf(sleepStage(start, end)),
        )

        val result = SleepDataNormalizer.normalize(listOf(session), zoneOffset)

        assertEquals(1, result.size)
        assertEquals(date, result[0].date)
        assertEquals(Duration.ofMinutes(2), result[0].duration)
    }

    @Test
    fun multipleOvernightSessions_areAttributedToTheirWakeDates() {
        val day1 = date.minusDays(1)
        val day2 = date
        val session1Start = day1.atTime(23, 0).toInstant(zoneOffset)
        val session1End = day2.atTime(7, 0).toInstant(zoneOffset)
        val session1 = sleepSession(
            start = session1Start,
            end = session1End,
            stages = listOf(sleepStage(session1Start, session1End)),
        )
        val session2Start = day2.atTime(23, 0).toInstant(zoneOffset)
        val session2End = day2.plusDays(1).atTime(7, 0).toInstant(zoneOffset)
        val session2 = sleepSession(
            start = session2Start,
            end = session2End,
            stages = listOf(sleepStage(session2Start, session2End)),
        )

        val result = SleepDataNormalizer.normalize(listOf(session2, session1), zoneOffset)

        assertEquals(2, result.size)
        assertEquals(day2, result[0].date)
        assertEquals(day2.plusDays(1), result[1].date)
        assertEquals(Duration.ofHours(8), result[0].duration)
        assertEquals(Duration.ofHours(8), result[1].duration)
    }

    @Test
    fun eveningNapBeforeMidnight_isAttributedToSameDay() {
        val start = date.atTime(20, 0).toInstant(zoneOffset)
        val end = date.atTime(22, 0).toInstant(zoneOffset)
        val session = sleepSession(
            start = start,
            end = end,
            stages = listOf(sleepStage(start, end)),
        )

        val result = SleepDataNormalizer.normalize(listOf(session), zoneOffset)

        assertEquals(1, result.size)
        assertEquals(date, result[0].date)
        assertEquals(Duration.ofHours(2), result[0].duration)
    }

    @Test
    fun multipleSessionsOnSameDay_areAggregated() {
        val sessionAStart = date.atTime(20, 0).toInstant(zoneOffset)
        val sessionAEnd = date.atTime(22, 0).toInstant(zoneOffset)
        val sessionBStart = date.atTime(22, 30).toInstant(zoneOffset)
        val sessionBEnd = date.atTime(23, 30).toInstant(zoneOffset)

        val sessions = listOf(
            sleepSession(
                start = sessionAStart,
                end = sessionAEnd,
                stages = listOf(sleepStage(sessionAStart, sessionAEnd)),
            ),
            sleepSession(
                start = sessionBStart,
                end = sessionBEnd,
                stages = listOf(sleepStage(sessionBStart, sessionBEnd)),
            ),
        )

        val result = SleepDataNormalizer.normalize(sessions, zoneOffset)

        assertEquals(1, result.size)
        assertEquals(date, result[0].date)
        assertEquals(Duration.ofHours(3), result[0].duration)
        assertEquals(1.0f, result[0].efficiency)
        assertEquals(1.0f, result[0].continuity)
    }

    @Test
    fun sessionWithoutStages_usesSessionDurationAndLeavesMetricsNull() {
        val start = date.atTime(22, 0).toInstant(zoneOffset)
        val end = date.plusDays(1).atTime(6, 0).toInstant(zoneOffset)
        val session = sleepSession(
            start = start,
            end = end,
            stages = emptyList(),
        )

        val result = SleepDataNormalizer.normalize(listOf(session), zoneOffset)

        assertEquals(1, result.size)
        assertEquals(Duration.ofHours(8), result[0].duration)
        assertNull(result[0].efficiency)
        assertNull(result[0].continuity)
    }

    @Test
    fun sessionWithAwakeInterruption_computesEfficiencyAndContinuity() {
        val start = date.atTime(22, 0).toInstant(zoneOffset)
        val interruptionStart = date.plusDays(1).atTime(2, 0).toInstant(zoneOffset)
        val interruptionEnd = date.plusDays(1).atTime(2, 30).toInstant(zoneOffset)
        val end = date.plusDays(1).atTime(6, 0).toInstant(zoneOffset)
        val session = sleepSession(
            start = start,
            end = end,
            stages = listOf(
                sleepStage(start, interruptionStart),
                awakeStage(interruptionStart, interruptionEnd),
                sleepStage(interruptionEnd, end),
            ),
        )

        val result = SleepDataNormalizer.normalize(listOf(session), zoneOffset)

        assertEquals(Duration.ofHours(7).plusMinutes(30), result[0].duration)
        assertEquals(0.9375f, result[0].efficiency!!, 0.0001f)
        assertEquals(0.5333f, result[0].continuity!!, 0.0001f)
    }

    @Test
    fun sessionWithOutOfBedTime_excludesItFromEfficiencyDenominator() {
        val start = date.atTime(22, 0).toInstant(zoneOffset)
        val outOfBedStart = date.plusDays(1).atTime(2, 0).toInstant(zoneOffset)
        val outOfBedEnd = date.plusDays(1).atTime(2, 30).toInstant(zoneOffset)
        val end = date.plusDays(1).atTime(6, 0).toInstant(zoneOffset)
        val session = sleepSession(
            start = start,
            end = end,
            stages = listOf(
                sleepStage(start, outOfBedStart),
                outOfBedStage(outOfBedStart, outOfBedEnd),
                sleepStage(outOfBedEnd, end),
            ),
        )

        val result = SleepDataNormalizer.normalize(listOf(session), zoneOffset)

        assertEquals(Duration.ofHours(7).plusMinutes(30), result[0].duration)
        assertEquals(1.0f, result[0].efficiency!!, 0.0001f)
    }

    @Test
    fun emptySessionList_returnsEmptyList() {
        val result = SleepDataNormalizer.normalize(emptyList(), zoneOffset)

        assertTrue(result.isEmpty())
    }

    @Test
    fun multipleDays_areSortedByDate() {
        val day1 = date
        val day2 = date.plusDays(1)
        val session1 = sleepSession(
            start = day1.atTime(22, 0).toInstant(zoneOffset),
            end = day1.atTime(23, 0).toInstant(zoneOffset),
            stages = listOf(
                sleepStage(day1.atTime(22, 0).toInstant(zoneOffset), day1.atTime(23, 0).toInstant(zoneOffset)),
            ),
        )
        val session2 = sleepSession(
            start = day2.atTime(22, 0).toInstant(zoneOffset),
            end = day2.atTime(23, 0).toInstant(zoneOffset),
            stages = listOf(
                sleepStage(day2.atTime(22, 0).toInstant(zoneOffset), day2.atTime(23, 0).toInstant(zoneOffset)),
            ),
        )

        val result = SleepDataNormalizer.normalize(listOf(session2, session1), zoneOffset)

        assertEquals(2, result.size)
        assertEquals(day1, result[0].date)
        assertEquals(day2, result[1].date)
    }

    @Test
    fun aggregatingSessions_weightedAveragesMetrics() {
        val sessionAStart = date.atTime(20, 0).toInstant(zoneOffset)
        val sessionAEnd = date.atTime(22, 0).toInstant(zoneOffset)
        val sessionBStart = date.atTime(23, 0).toInstant(zoneOffset)
        val sessionBEnd = date.atTime(23, 30).toInstant(zoneOffset)

        // Session A: 2 hours of sleep, one 1-hour awake interruption -> efficiency 0.5
        val sessionAMiddle = date.atTime(21, 0).toInstant(zoneOffset)
        val sessionA = sleepSession(
            start = sessionAStart,
            end = sessionAEnd,
            stages = listOf(
                sleepStage(sessionAStart, sessionAMiddle),
                awakeStage(sessionAMiddle, sessionAEnd),
            ),
        )

        // Session B: 30 minutes of uninterrupted sleep -> efficiency 1.0
        val sessionB = sleepSession(
            start = sessionBStart,
            end = sessionBEnd,
            stages = listOf(sleepStage(sessionBStart, sessionBEnd)),
        )

        val result = SleepDataNormalizer.normalize(listOf(sessionA, sessionB), zoneOffset)

        assertEquals(1, result.size)
        // (0.5 * 3600 + 1.0 * 1800) / 5400 = 0.6667
        assertEquals(0.6667f, result[0].efficiency!!, 0.0001f)
    }

    private fun sleepSession(
        start: Instant,
        end: Instant,
        stages: List<Stage>,
    ): SleepSessionRecord {
        return SleepSessionRecord(
            startTime = start,
            startZoneOffset = zoneOffset,
            endTime = end,
            endZoneOffset = zoneOffset,
            metadata = androidx.health.connect.client.records.metadata.Metadata.activelyRecorded(
                androidx.health.connect.client.records.metadata.Device(
                    type = androidx.health.connect.client.records.metadata.Device.TYPE_UNKNOWN,
                ),
            ),
            title = null,
            notes = null,
            stages = stages,
        )
    }

    private fun sleepStage(start: Instant, end: Instant): Stage {
        return Stage(
            startTime = start,
            endTime = end,
            stage = SleepSessionRecord.STAGE_TYPE_SLEEPING,
        )
    }

    private fun awakeStage(start: Instant, end: Instant): Stage {
        return Stage(
            startTime = start,
            endTime = end,
            stage = SleepSessionRecord.STAGE_TYPE_AWAKE,
        )
    }

    private fun outOfBedStage(start: Instant, end: Instant): Stage {
        return Stage(
            startTime = start,
            endTime = end,
            stage = SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
        )
    }
}
