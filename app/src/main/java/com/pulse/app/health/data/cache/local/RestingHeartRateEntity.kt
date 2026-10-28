package com.pulse.app.health.data.cache.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "resting_heart_rate_cache")
internal data class RestingHeartRateEntity(
    @PrimaryKey
    @ColumnInfo(name = "date")
    val date: String,

    @ColumnInfo(name = "bpm")
    val bpm: Int,

    @ColumnInfo(name = "synced_at_millis")
    val syncedAtMillis: Long,
)
