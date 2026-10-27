package com.pulse.app.health.permission

import org.junit.Assert.assertEquals
import org.junit.Test

class HealthPermissionStateResolverTest {

    private val required = setOf(
        PulseHealthPermission.SLEEP,
        PulseHealthPermission.STEPS,
        PulseHealthPermission.RESTING_HEART_RATE,
        PulseHealthPermission.HRV,
        PulseHealthPermission.HYDRATION,
    )

    @Test
    fun allGranted_returnsGranted() {
        val state = HealthPermissionStateResolver.resolve(required, required)

        assertEquals(HealthPermissionState.Granted, state)
    }

    @Test
    fun noneGranted_returnsDenied() {
        val state = HealthPermissionStateResolver.resolve(required, emptySet())

        assertEquals(HealthPermissionState.Denied, state)
    }

    @Test
    fun someGranted_returnsPartiallyGranted() {
        val granted = setOf(PulseHealthPermission.SLEEP, PulseHealthPermission.STEPS)
        val state = HealthPermissionStateResolver.resolve(required, granted)

        val expected = HealthPermissionState.PartiallyGranted(
            granted = granted,
            denied = required - granted,
        )
        assertEquals(expected, state)
    }

    @Test
    fun partialState_containsCorrectDeniedPermissions() {
        val granted = setOf(PulseHealthPermission.HRV)
        val state = HealthPermissionStateResolver.resolve(required, granted)

        assertEquals(
            required - granted,
            (state as HealthPermissionState.PartiallyGranted).denied,
        )
    }
}
