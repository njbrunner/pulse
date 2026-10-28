package com.pulse.app.health.data.cache.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "heart_rate_variability_cache")
internal data class HeartRateVariabilityEntity(
    @PrimaryKey
    @ColumnInfo(name = "date")
    val date: String,

    @ColumnInfo(name = "ms")
    val ms: Int,

    @ColumnInfo(name = "synced_at_millis")
    val syncedAtMillis: Long,
)
