package com.pulse.app.debug

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class HeartRateDebugStatisticsTest {

    @Test
    fun percentile_singleValue_returnsValue() {
        assertEquals(42.0, percentile(listOf(42L), 50.0), 0.0001)
    }

    @Test
    fun percentile_twoValues_linearlyInterpolates() {
        // Excel PERCENTILE.INC({10, 20}, 0.5) = 15
        assertEquals(15.0, percentile(listOf(10L, 20L), 50.0), 0.0001)
    }

    @Test
    fun percentile_knownDataset_matchesExcelPercentileInc() {
        // Excel PERCENTILE.INC({1..10}, p) for several percentiles.
        val values = (1L..10L).toList()

        assertEquals(1.09, percentile(values, 1.0), 0.0001)
        assertEquals(1.18, percentile(values, 2.0), 0.0001)
        assertEquals(1.27, percentile(values, 3.0), 0.0001)
        assertEquals(1.45, percentile(values, 5.0), 0.0001)
        assertEquals(5.5, percentile(values, 50.0), 0.0001)
    }

    @Test
    fun percentile_duplicates_handlesCorrectly() {
        val values = listOf(50L, 50L, 60L, 60L)
        assertEquals(50.0, percentile(values, 25.0), 0.0001)
        assertEquals(55.0, percentile(values, 50.0), 0.0001)
    }

    @Test
    fun findLowestWindowMean_emptySamples_returnsNull() {
        val result = findLowestWindowMean(
            samples = emptyList(),
            windowDuration = Duration.ofMinutes(5),
            minSamples = 5,
        )
        assertNull(result)
    }

    @Test
    fun findLowestWindowMean_insufficientSamples_returnsNull() {
        val samples = listOf(
            sample(time = 0, bpm = 50),
            sample(time = 60, bpm = 52),
        )
        val result = findLowestWindowMean(
            samples = samples,
            windowDuration = Duration.ofMinutes(5),
            minSamples = 5,
        )
        assertNull(result)
    }

    @Test
    fun findLowestWindowMean_singleQualifyingWindow_returnsWindowMean() {
        val samples = listOf(
            sample(time = 0, bpm = 50),
            sample(time = 60, bpm = 52),
            sample(time = 120, bpm = 54),
        )
        val result = findLowestWindowMean(
            samples = samples,
            windowDuration = Duration.ofMinutes(5),
            minSamples = 3,
        )
        assertEquals(52.0, result!!.meanBpm, 0.0001)
        assertEquals(3, result.sampleCount)
    }

    @Test
    fun findLowestWindowMean_selectsLowestMeanWindow() {
        // Five samples over 10 minutes. The lowest 5-minute window covers the
        // three low-BPM samples in the middle: mean = (50 + 50 + 52) / 3.
        val samples = listOf(
            sample(time = 0, bpm = 100),
            sample(time = 120, bpm = 50),
            sample(time = 180, bpm = 50),
            sample(time = 240, bpm = 52),
            sample(time = 600, bpm = 100),
        )
        val result = findLowestWindowMean(
            samples = samples,
            windowDuration = Duration.ofMinutes(5),
            minSamples = 3,
        )
        assertEquals(50.667, result!!.meanBpm, 0.001)
        assertEquals(3, result.sampleCount)
    }

    @Test
    fun findLowestWindowMean_excludesSparseWindow() {
        // A sparse window contains only one low sample; a denser window contains
        // four samples averaging 55 BPM. With minSamples = 3, the sparse window
        // must not win.
        val samples = listOf(
            sample(time = 0, bpm = 40),
            sample(time = 30, bpm = 60),
            sample(time = 60, bpm = 60),
            sample(time = 90, bpm = 60),
            sample(time = 600, bpm = 30),
        )
        val result = findLowestWindowMean(
            samples = samples,
            windowDuration = Duration.ofMinutes(5),
            minSamples = 3,
        )
        assertEquals(55.0, result!!.meanBpm, 0.0001)
        assertTrue(result.sampleCount >= 3)
    }

    @Test
    fun findLowestWindowMean_findsGlobalMinimumOverContinuousStarts() {
        // The true lowest 5-minute mean occurs with a window start that is not
        // exactly aligned to any sample time. Candidate start times include
        // sample times and (sample time - window duration), so it should be found.
        val samples = listOf(
            sample(time = 0, bpm = 100),
            sample(time = 61, bpm = 50),
            sample(time = 359, bpm = 50),
            sample(time = 360, bpm = 100),
        )
        val result = findLowestWindowMean(
            samples = samples,
            windowDuration = Duration.ofMinutes(5),
            minSamples = 2,
        )
        assertEquals(50.0, result!!.meanBpm, 0.0001)
        assertEquals(2, result.sampleCount)
    }

    @Test
    fun findLowestWindowMean_tenMinuteWindowHasSeparateMinimum() = runTest {
        // A 10-minute window is long enough to span from the first low sample to
        // the last, but because the low samples span 0..300s, every 600s window
        // that contains all four low samples also includes one of the 100-BPM
        // boundary samples. The lowest qualifying mean is therefore 61.0 across
        // five samples.
        val samples = listOf(
            sample(time = 0, bpm = 100),
            sample(time = 120, bpm = 50),
            sample(time = 180, bpm = 50),
            sample(time = 240, bpm = 52),
            sample(time = 300, bpm = 53),
            sample(time = 600, bpm = 100),
        )
        val result = findLowestWindowMean(
            samples = samples,
            windowDuration = Duration.ofMinutes(10),
            minSamples = 4,
        )
        assertEquals(61.0, result!!.meanBpm, 0.0001)
        assertEquals(5, result.sampleCount)
    }

    @Test
    fun formatWindow_includesMeanTimeRangeAndCount() {
        val result = LowestWindowResult(
            meanBpm = 47.8,
            start = Instant.parse("2026-10-30T05:55:00Z"),
            end = Instant.parse("2026-10-30T06:00:00Z"),
            sampleCount = 42,
        )
        val formatted = formatWindow(result, ZoneId.of("UTC"))
        assertEquals("47.8 (05:55-06:00, n=42)", formatted)
    }

    private fun sample(time: Long, bpm: Long): HeartRateSample {
        return HeartRateSample(
            time = Instant.ofEpochSecond(time),
            bpm = bpm,
        )
    }
}
