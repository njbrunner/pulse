package com.pulse.app.debug

import androidx.activity.ComponentActivity
import com.pulse.app.health.data.HealthConnectDataRepository

/**
 * Release no-op counterpart of [HydrationDebugVerification]. Keeps the
 * MainActivity call site the same in both build variants while ensuring debug
 * behavior is completely absent from release builds.
 */
internal object HydrationDebugVerification {
    fun trigger(activity: ComponentActivity, repository: HealthConnectDataRepository) = Unit
}
