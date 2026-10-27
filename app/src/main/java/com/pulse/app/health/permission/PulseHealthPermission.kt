package com.pulse.app.health.permission

/**
 * Pulse's required health permissions. Each value maps to a single Google Health Connect
 * record type that the app needs read access to.
 */
enum class PulseHealthPermission {
    SLEEP,
    STEPS,
    RESTING_HEART_RATE,
    HRV,
    HYDRATION,
}
