package com.pulse.app.health.permission

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Factory for creating [HealthPermissionViewModel] instances.
 */
class HealthPermissionViewModelFactory(
    private val permissionManager: HealthPermissionManager,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(HealthPermissionViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return HealthPermissionViewModel(permissionManager) as T
    }
}
