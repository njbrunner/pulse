package com.pulse.app.health.data

import com.pulse.app.health.data.model.StepsSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HealthDataResultTest {

    @Test
    fun success_holdsData() {
        val steps = StepsSummary(date = LocalDate.of(2026, 10, 28), steps = 5_000L)

        val result = HealthDataResult.Success(steps)

        assertEquals(steps, result.data)
    }

    @Test
    fun unavailable_isDistinctFromOtherStates() {
        val result: HealthDataResult<StepsSummary> = HealthDataResult.Unavailable

        assertTrue(result is HealthDataResult.Unavailable)
    }

    @Test
    fun permissionDenied_isDistinctFromOtherStates() {
        val result: HealthDataResult<StepsSummary> = HealthDataResult.PermissionDenied

        assertTrue(result is HealthDataResult.PermissionDenied)
    }

    @Test
    fun failure_holdsThrowable() {
        val error = IllegalStateException("API failure")

        val result = HealthDataResult.Failure(error)

        assertEquals(error, result.error)
    }
}
