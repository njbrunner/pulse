package com.pulse.app.health.permission

import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract

/**
 * Test double for [HealthPermissionManager].
 */
class FakeHealthPermissionManager(
    override val requiredPermissions: Set<PulseHealthPermission> = setOf(
        PulseHealthPermission.SLEEP,
        PulseHealthPermission.STEPS,
        PulseHealthPermission.RESTING_HEART_RATE,
        PulseHealthPermission.HRV,
        PulseHealthPermission.HYDRATION,
    ),
) : HealthPermissionManager {

    var checkPermissionsResult: HealthPermissionState = HealthPermissionState.Denied
    private var lastGrantedResult: Set<PulseHealthPermission> = emptySet()

    override val requestContract: ActivityResultContract<Unit, Set<PulseHealthPermission>> =
        object : ActivityResultContract<Unit, Set<PulseHealthPermission>>() {
            override fun createIntent(context: android.content.Context, input: Unit): Intent {
                return Intent()
            }

            override fun parseResult(resultCode: Int, intent: Intent?): Set<PulseHealthPermission> {
                return lastGrantedResult
            }
        }

    override suspend fun checkPermissions(): HealthPermissionState {
        return checkPermissionsResult
    }

    fun setGrantedResult(granted: Set<PulseHealthPermission>) {
        lastGrantedResult = granted
    }
}
