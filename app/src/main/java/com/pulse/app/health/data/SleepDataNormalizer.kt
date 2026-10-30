package com.pulse.app.health.data

import androidx.health.connect.client.records.SleepSessionRecord
import com.pulse.app.health.data.model.SleepSummary
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/**
 * Normalizes Google Health Connect [SleepSessionRecord]s into application-level
 * [SleepSummary] models.
 *
 * A session is attributed to the calendar day that contains its midpoint. For a
 * normal overnight session this is the wake/end date (e.g. a session from 23:00
 * to 07:00 is attributed to the day containing 03:00, which is the wake date).
 * This consistently handles sessions that cross midnight.
 *
 * When stage data is unavailable, the session duration is used as the sleep
 * duration and efficiency/continuity are left null.
 */
internal object SleepDataNormalizer {

    /**
     * Normalize a list of [SleepSessionRecord]s into [SleepSummary]s grouped and
     * aggregated by calendar day.
     */
    fun normalize(
        sessions: List<SleepSessionRecord>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<SleepSummary> {
        return sessions
            .mapNotNull { normalizeSession(it, zoneId) }
            .groupBy { it.date }
            .map { (date, sessionsForDate) -> aggregateSessions(date, sessionsForDate) }
            .sortedBy { it.date }
    }

    private fun normalizeSession(
        session: SleepSessionRecord,
        fallbackZoneId: ZoneId,
    ): NormalizedSleepSession? {
        val sessionDuration = Duration.between(session.startTime, session.endTime)
        if (sessionDuration.isZero || sessionDuration.isNegative) {
            return null
        }

        val midpoint = session.startTime.plus(sessionDuration.dividedBy(2))
        val zoneId = session.zoneId() ?: fallbackZoneId
        val date = midpoint.atZone(zoneId).toLocalDate()

        val stages = session.stages
        if (stages.isNullOrEmpty()) {
            return NormalizedSleepSession(
                date = date,
                duration = sessionDuration,
                efficiency = null,
                continuity = null,
            )
        }

        var sleepingDuration = Duration.ZERO
        var outOfBedDuration = Duration.ZERO
        var longestSleepingSegment = Duration.ZERO
        var currentSleepingSegment = Duration.ZERO

        stages.forEach { stage ->
            val stageDuration = Duration.between(stage.startTime, stage.endTime)
            when (stage.stage) {
                SleepSessionRecord.STAGE_TYPE_SLEEPING,
                SleepSessionRecord.STAGE_TYPE_LIGHT,
                SleepSessionRecord.STAGE_TYPE_DEEP,
                SleepSessionRecord.STAGE_TYPE_REM,
                -> {
                    sleepingDuration += stageDuration
                    currentSleepingSegment += stageDuration
                }

                SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> {
                    outOfBedDuration += stageDuration
                    longestSleepingSegment = maxOf(longestSleepingSegment, currentSleepingSegment)
                    currentSleepingSegment = Duration.ZERO
                }

                SleepSessionRecord.STAGE_TYPE_AWAKE,
                SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
                SleepSessionRecord.STAGE_TYPE_UNKNOWN,
                -> {
                    // Awake and unknown stages end the current continuous sleep segment.
                    longestSleepingSegment = maxOf(longestSleepingSegment, currentSleepingSegment)
                    currentSleepingSegment = Duration.ZERO
                }
            }
        }
        longestSleepingSegment = maxOf(longestSleepingSegment, currentSleepingSegment)

        val inBedDuration = sessionDuration - outOfBedDuration
        val efficiency = if (inBedDuration > Duration.ZERO) {
            sleepingDuration.seconds.toFloat() / inBedDuration.seconds.toFloat()
        } else {
            null
        }

        val continuity = if (sleepingDuration > Duration.ZERO) {
            longestSleepingSegment.seconds.toFloat() / sleepingDuration.seconds.toFloat()
        } else {
            null
        }

        return NormalizedSleepSession(
            date = date,
            duration = sleepingDuration,
            efficiency = efficiency,
            continuity = continuity,
        )
    }

    private fun aggregateSessions(
        date: LocalDate,
        sessions: List<NormalizedSleepSession>,
    ): SleepSummary {
        val totalDuration = sessions.fold(Duration.ZERO) { acc, session ->
            acc + session.duration
        }
        val totalSleepingSeconds = totalDuration.seconds

        val efficiency = weightedAverage(
            values = sessions.mapNotNull { session ->
                session.efficiency?.let { it to session.duration.seconds }
            },
        )

        val continuity = weightedAverage(
            values = sessions.mapNotNull { session ->
                session.continuity?.let { it to session.duration.seconds }
            },
        )

        return SleepSummary(
            date = date,
            duration = totalDuration,
            efficiency = efficiency,
            continuity = continuity,
        )
    }

    private fun weightedAverage(values: List<Pair<Float, Long>>): Float? {
        if (values.isEmpty()) {
            return null
        }
        val totalWeight = values.sumOf { it.second }
        if (totalWeight == 0L) {
            return null
        }
        val weightedSum = values.sumOf { (value, weight) ->
            value.toDouble() * weight
        }
        return (weightedSum / totalWeight).toFloat()
    }

    private fun SleepSessionRecord.zoneId(): ZoneId? {
        val offset = endZoneOffset ?: startZoneOffset ?: return null
        return ZoneId.ofOffset("UTC", offset)
    }

    private data class NormalizedSleepSession(
        val date: LocalDate,
        val duration: Duration,
        val efficiency: Float?,
        val continuity: Float?,
    )
}
