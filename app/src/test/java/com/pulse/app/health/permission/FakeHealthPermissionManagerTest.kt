package com.pulse.app.health.permission

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeHealthPermissionManagerTest {

    private val manager = FakeHealthPermissionManager()

    @Test
    fun checkPermissions_returnsConfiguredResult() = runTest {
        manager.checkPermissionsResult = HealthPermissionState.Granted

        val state = manager.checkPermissions()

        assertEquals(HealthPermissionState.Granted, state)
    }

    @Test
    fun resolveState_usesDefaultResolver() {
        val granted = setOf(PulseHealthPermission.SLEEP)

        val state = manager.resolveState(granted)

        val expected = HealthPermissionState.PartiallyGranted(
            granted = granted,
            denied = manager.requiredPermissions - granted,
        )
        assertEquals(expected, state)
    }

    @Test
    fun requiredPermissions_matchesPulseRequirements() {
        val expected = setOf(
            PulseHealthPermission.SLEEP,
            PulseHealthPermission.STEPS,
            PulseHealthPermission.HEART_RATE,
            PulseHealthPermission.RESTING_HEART_RATE,
            PulseHealthPermission.HRV,
            PulseHealthPermission.HYDRATION,
        )

        assertEquals(expected, manager.requiredPermissions)
    }
}
