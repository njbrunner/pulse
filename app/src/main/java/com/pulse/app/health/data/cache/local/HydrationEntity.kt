package com.pulse.app.health.data.cache.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hydration_cache")
internal data class HydrationEntity(
    @PrimaryKey
    @ColumnInfo(name = "date")
    val date: String,

    @ColumnInfo(name = "liters")
    val liters: Double,

    @ColumnInfo(name = "synced_at_millis")
    val syncedAtMillis: Long,
)
