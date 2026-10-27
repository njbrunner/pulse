package com.pulse.app.health.permission

/**
 * Pure helper for resolving a set of granted permissions into a [HealthPermissionState].
 */
object HealthPermissionStateResolver {

    fun resolve(
        required: Set<PulseHealthPermission>,
        granted: Set<PulseHealthPermission>,
    ): HealthPermissionState {
        return when {
            granted.containsAll(required) -> HealthPermissionState.Granted
            granted.isEmpty() -> HealthPermissionState.Denied
            else -> HealthPermissionState.PartiallyGranted(
                granted = granted,
                denied = required - granted,
            )
        }
    }
}
