package com.pulse.app.health.permission

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel that exposes the current Google Health Connect permission state and reacts
 * to the result of a permission request.
 */
class HealthPermissionViewModel(
    private val permissionManager: HealthPermissionManager,
) : ViewModel() {

    private val _permissionState = MutableStateFlow<HealthPermissionState>(HealthPermissionState.Loading)
    val permissionState: StateFlow<HealthPermissionState> = _permissionState.asStateFlow()

    init {
        refreshPermissionState()
    }

    fun refreshPermissionState() {
        viewModelScope.launch {
            _permissionState.value = permissionManager.checkPermissions()
        }
    }

    fun onPermissionResult(granted: Set<PulseHealthPermission>) {
        _permissionState.value = permissionManager.resolveState(granted)
    }
}
