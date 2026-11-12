package com.pulse.app.health.data

import androidx.health.connect.client.records.StepsRecord
import com.pulse.app.health.data.model.StepsSummary
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToLong

/**
 * Normalizes Google Health Connect [StepsRecord]s into application-level
 * [StepsSummary] models.
 *
 * Each [StepsRecord] represents a step count over a time interval. Records are
 * attributed to local calendar days and summed per day. When a record crosses a
 * local day boundary, its count is distributed proportionally by the duration
 * spent in each day so daily totals respect the device's time zone.
 */
internal object StepsDataNormalizer {

    /**
     * Normalize a list of [StepsRecord]s into [StepsSummary]s grouped and summed
     * by local calendar day.
     *
     * Days with no covering records are omitted from the result, allowing the
     * caller to distinguish unavailable days from a recorded zero.
     */
    fun normalize(
        records: List<StepsRecord>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<StepsSummary> {
        if (records.isEmpty()) {
            return emptyList()
        }

        val stepsByDate = mutableMapOf<LocalDate, Long>()

        records.forEach { record ->
            val recordDuration = Duration.between(record.startTime, record.endTime)
            if (recordDuration.isZero || recordDuration.isNegative) {
                return@forEach
            }

            val startDate = record.startTime.atZone(record.startZoneOffset ?: zoneId).toLocalDate()
            val endDate = record.endTime.atZone(record.endZoneOffset ?: zoneId).toLocalDate()

            if (startDate == endDate) {
                stepsByDate[startDate] = stepsByDate.getOrDefault(startDate, 0L) + record.count
                return@forEach
            }

            distributeAcrossDays(record, startDate, endDate, recordDuration, zoneId)
                .forEach { (date, portion) ->
                    stepsByDate[date] = stepsByDate.getOrDefault(date, 0L) + portion
                }
        }

        return stepsByDate
            .map { (date, steps) -> StepsSummary(date = date, steps = steps) }
            .sortedBy { it.date }
    }

    private fun distributeAcrossDays(
        record: StepsRecord,
        startDate: LocalDate,
        endDate: LocalDate,
        totalDuration: Duration,
        fallbackZoneId: ZoneId,
    ): List<Pair<LocalDate, Long>> {
        val portions = mutableListOf<Pair<LocalDate, Long>>()
        var remainingCount = record.count
        var currentDate = startDate

        while (!currentDate.isAfter(endDate)) {
            val dayStart = currentDate.atStartOfDay(fallbackZoneId).toInstant()
            val dayEnd = currentDate.plusDays(1).atStartOfDay(fallbackZoneId).toInstant()

            val segmentStart = maxOf(record.startTime, dayStart)
            val segmentEnd = minOf(record.endTime, dayEnd)
            val segmentDuration = Duration.between(segmentStart, segmentEnd)

            if (segmentDuration.isPositive) {
                val isLastDay = currentDate == endDate
                val portion = if (isLastDay) {
                    remainingCount
                } else {
                    val ratio = segmentDuration.toMillis().toDouble() / totalDuration.toMillis()
                    val calculated = (record.count * ratio).roundToLong()
                    calculated
                }

                if (portion > 0L) {
                    portions.add(currentDate to portion)
                    remainingCount -= portion
                }
            }

            currentDate = currentDate.plusDays(1)
        }

        return portions
    }
}
