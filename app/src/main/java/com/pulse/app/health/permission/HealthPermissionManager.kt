package com.pulse.app.health.permission

import androidx.activity.result.contract.ActivityResultContract

/**
 * Manages Google Health Connect authorization for Pulse.
 */
interface HealthPermissionManager {

    /**
     * The complete set of permissions Pulse needs.
     */
    val requiredPermissions: Set<PulseHealthPermission>

    /**
     * Returns an [ActivityResultContract] that can be registered in an Activity or Fragment
     * to request the required permissions. The parsed result is the set of permissions the
     * user granted in the request flow.
     */
    val requestContract: ActivityResultContract<Unit, Set<PulseHealthPermission>>

    /**
     * Checks whether Health Connect is available and returns the current permission state.
     */
    suspend fun checkPermissions(): HealthPermissionState

    /**
     * Resolves the permission state for the given set of [granted] permissions.
     */
    fun resolveState(granted: Set<PulseHealthPermission>): HealthPermissionState {
        return HealthPermissionStateResolver.resolve(requiredPermissions, granted)
    }
}
