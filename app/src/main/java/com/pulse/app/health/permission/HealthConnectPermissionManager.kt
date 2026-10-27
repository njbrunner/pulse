package com.pulse.app.health.permission

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord

/**
 * [HealthPermissionManager] implementation backed by Google Health Connect.
 */
class HealthConnectPermissionManager(
    private val applicationContext: Context,
    private val healthConnectClient: HealthConnectClient? = null,
) : HealthPermissionManager {

    private val client: HealthConnectClient? by lazy {
        healthConnectClient ?: when (HealthConnectClient.getSdkStatus(applicationContext)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectClient.getOrCreate(applicationContext)
            else -> null
        }
    }

    override val requiredPermissions: Set<PulseHealthPermission> = setOf(
        PulseHealthPermission.SLEEP,
        PulseHealthPermission.STEPS,
        PulseHealthPermission.RESTING_HEART_RATE,
        PulseHealthPermission.HRV,
        PulseHealthPermission.HYDRATION,
    )

    private val requiredHealthPermissions: Set<String> by lazy {
        requiredPermissions.mapTo(mutableSetOf(), ::toHealthPermission)
    }

    private val healthPermissionToPulseMap: Map<String, PulseHealthPermission> by lazy {
        requiredPermissions.associateBy(::toHealthPermission)
    }

    override val requestContract: ActivityResultContract<Unit, Set<PulseHealthPermission>> =
        PulsePermissionRequestContract()

    override suspend fun checkPermissions(): HealthPermissionState {
        val currentClient = client ?: return HealthPermissionState.Unavailable
        val granted = currentClient.permissionController.getGrantedPermissions()
            .mapNotNull(::toPulsePermission)
            .toSet()
        return resolveState(granted)
    }

    private fun toHealthPermission(permission: PulseHealthPermission): String {
        return when (permission) {
            PulseHealthPermission.SLEEP ->
                HealthPermission.getReadPermission(SleepSessionRecord::class)
            PulseHealthPermission.STEPS ->
                HealthPermission.getReadPermission(StepsRecord::class)
            PulseHealthPermission.RESTING_HEART_RATE ->
                HealthPermission.getReadPermission(RestingHeartRateRecord::class)
            PulseHealthPermission.HRV ->
                HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class)
            PulseHealthPermission.HYDRATION ->
                HealthPermission.getReadPermission(HydrationRecord::class)
        }
    }

    private fun toPulsePermission(permission: String): PulseHealthPermission? {
        return healthPermissionToPulseMap[permission]
    }

    private inner class PulsePermissionRequestContract :
        ActivityResultContract<Unit, Set<PulseHealthPermission>>() {

        private val delegate = PermissionController.createRequestPermissionResultContract()

        override fun createIntent(context: Context, input: Unit): Intent {
            return delegate.createIntent(context, requiredHealthPermissions)
        }

        override fun parseResult(resultCode: Int, intent: Intent?): Set<PulseHealthPermission> {
            val granted = delegate.parseResult(resultCode, intent) ?: emptySet()
            return granted.mapNotNull(::toPulsePermission).toSet()
        }
    }
}
