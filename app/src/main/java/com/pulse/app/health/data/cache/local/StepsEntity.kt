package com.pulse.app.health.data.cache.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "steps_cache")
internal data class StepsEntity(
    @PrimaryKey
    @ColumnInfo(name = "date")
    val date: String,

    @ColumnInfo(name = "steps")
    val steps: Long,

    @ColumnInfo(name = "synced_at_millis")
    val syncedAtMillis: Long,
)
