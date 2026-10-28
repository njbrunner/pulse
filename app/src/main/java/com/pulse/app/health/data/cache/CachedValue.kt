package com.pulse.app.health.data.cache

import java.time.Instant

/**
 * A value stored in [HealthDataCache] together with the time it was last synced.
 */
data class CachedValue<T>(
    val data: T,
    val syncedAt: Instant,
)
