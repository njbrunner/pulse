package com.pulse.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration

class DailyWidgetFormattersTest {

    @Test
    fun formatDuration_hoursAndMinutes() {
        assertEquals(
            "7h 32m",
            DailyWidgetFormatters.formatDuration(Duration.ofHours(7).plusMinutes(32)),
        )
    }

    @Test
    fun formatDuration_zero() {
        assertEquals(
            "0h 0m",
            DailyWidgetFormatters.formatDuration(Duration.ZERO),
        )
    }

    @Test
    fun formatDuration_null() {
        assertNull(DailyWidgetFormatters.formatDuration(null))
    }

    @Test
    fun formatDuration_negative() {
        assertNull(DailyWidgetFormatters.formatDuration(Duration.ofMinutes(-10)))
    }

    @Test
    fun formatScore_valid() {
        assertEquals("85", DailyWidgetFormatters.formatScore(85))
    }

    @Test
    fun formatScore_zero() {
        assertEquals("0", DailyWidgetFormatters.formatScore(0))
    }

    @Test
    fun formatScore_hundred() {
        assertEquals("100", DailyWidgetFormatters.formatScore(100))
    }

    @Test
    fun formatScore_null() {
        assertNull(DailyWidgetFormatters.formatScore(null))
    }

    @Test
    fun formatScore_outOfRange() {
        assertNull(DailyWidgetFormatters.formatScore(-1))
        assertNull(DailyWidgetFormatters.formatScore(101))
    }

    @Test
    fun formatSteps_valid() {
        assertEquals("6432", DailyWidgetFormatters.formatSteps(6432L))
    }

    @Test
    fun formatSteps_zero() {
        assertEquals("0", DailyWidgetFormatters.formatSteps(0L))
    }

    @Test
    fun formatSteps_null() {
        assertNull(DailyWidgetFormatters.formatSteps(null))
    }

    @Test
    fun formatSteps_negative() {
        assertNull(DailyWidgetFormatters.formatSteps(-1L))
    }

    @Test
    fun formatRestingHeartRate_valid() {
        assertEquals("62 bpm", DailyWidgetFormatters.formatRestingHeartRate(62))
    }

    @Test
    fun formatRestingHeartRate_null() {
        assertNull(DailyWidgetFormatters.formatRestingHeartRate(null))
    }

    @Test
    fun formatRestingHeartRate_zero() {
        assertNull(DailyWidgetFormatters.formatRestingHeartRate(0))
    }

    @Test
    fun formatRestingHeartRate_negative() {
        assertNull(DailyWidgetFormatters.formatRestingHeartRate(-1))
    }

    @Test
    fun formatWaterLiters_oneDecimal() {
        assertEquals("1.5 L", DailyWidgetFormatters.formatWaterLiters(1.5))
    }

    @Test
    fun formatWaterLiters_roundsToOneDecimal() {
        assertEquals("1.3 L", DailyWidgetFormatters.formatWaterLiters(1.25))
    }

    @Test
    fun formatWaterLiters_zero() {
        assertEquals("0.0 L", DailyWidgetFormatters.formatWaterLiters(0.0))
    }

    @Test
    fun formatWaterLiters_null() {
        assertNull(DailyWidgetFormatters.formatWaterLiters(null))
    }

    @Test
    fun formatWaterLiters_negative() {
        assertNull(DailyWidgetFormatters.formatWaterLiters(-0.1))
    }

    @Test
    fun formatProgress_zero() {
        assertEquals("0%", DailyWidgetFormatters.formatProgress(0f))
    }

    @Test
    fun formatProgress_fifty() {
        assertEquals("50%", DailyWidgetFormatters.formatProgress(0.5f))
    }

    @Test
    fun formatProgress_hundred() {
        assertEquals("100%", DailyWidgetFormatters.formatProgress(1f))
    }

    @Test
    fun formatProgress_coercesAboveOne() {
        assertEquals("100%", DailyWidgetFormatters.formatProgress(1.5f))
    }

    @Test
    fun formatProgress_null() {
        assertNull(DailyWidgetFormatters.formatProgress(null))
    }

    @Test
    fun formatProgress_negative() {
        assertNull(DailyWidgetFormatters.formatProgress(-0.1f))
    }
}
