package com.pulse.app.health.data.cache.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
internal interface SleepDao {

    @Upsert
    suspend fun upsert(entity: SleepEntity)

    @Upsert
    suspend fun upsertAll(entities: List<SleepEntity>)

    @Query("SELECT * FROM sleep_cache WHERE date = :date")
    suspend fun getByDate(date: String): SleepEntity?

    @Query(
        "SELECT * FROM sleep_cache " +
            "WHERE date BETWEEN :startDate AND :endDate " +
            "ORDER BY date ASC",
    )
    suspend fun getRange(startDate: String, endDate: String): List<SleepEntity>

    @Query("DELETE FROM sleep_cache")
    suspend fun clear()
}
