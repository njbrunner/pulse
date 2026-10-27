package com.pulse.app.health.permission

/**
 * Represents the authorization state for Google Health Connect permissions.
 */
sealed class HealthPermissionState {
    /**
     * Permission state is being determined.
     */
    data object Loading : HealthPermissionState()

    /**
     * Health Connect is not available on this device.
     */
    data object Unavailable : HealthPermissionState()

    /**
     * All required permissions are currently granted.
     */
    data object Granted : HealthPermissionState()

    /**
     * None of the required permissions are currently granted.
     */
    data object Denied : HealthPermissionState()

    /**
     * Some required permissions are granted and some are denied or revoked.
     */
    data class PartiallyGranted(
        val granted: Set<PulseHealthPermission>,
        val denied: Set<PulseHealthPermission>,
    ) : HealthPermissionState()
}
