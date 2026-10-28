package com.pulse.app.health.data.cache.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sleep_cache")
internal data class SleepEntity(
    @PrimaryKey
    @ColumnInfo(name = "date")
    val date: String,

    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Long,

    @ColumnInfo(name = "efficiency")
    val efficiency: Float?,

    @ColumnInfo(name = "continuity")
    val continuity: Float?,

    @ColumnInfo(name = "synced_at_millis")
    val syncedAtMillis: Long,
)
