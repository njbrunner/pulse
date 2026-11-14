package com.pulse.app.debug

import androidx.activity.ComponentActivity
import androidx.health.connect.client.HealthConnectClient
import com.pulse.app.health.data.HealthConnectDataRepository
import com.pulse.app.health.permission.HealthConnectPermissionManager

/**
 * Release no-op counterpart of [HydrationDebugVerification]. Keeps the
 * MainActivity call site the same in both build variants while ensuring debug
 * behavior is completely absent from release builds.
 */
internal object HydrationDebugVerification {
    fun trigger(
        activity: ComponentActivity,
        repository: HealthConnectDataRepository,
        client: HealthConnectClient?,
        permissionManager: HealthConnectPermissionManager?,
    ) = Unit
}
