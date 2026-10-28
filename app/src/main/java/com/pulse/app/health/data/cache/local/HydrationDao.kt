package com.pulse.app.health.data.cache.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
internal interface HydrationDao {

    @Upsert
    suspend fun upsert(entity: HydrationEntity)

    @Upsert
    suspend fun upsertAll(entities: List<HydrationEntity>)

    @Query("SELECT * FROM hydration_cache WHERE date = :date")
    suspend fun getByDate(date: String): HydrationEntity?

    @Query(
        "SELECT * FROM hydration_cache " +
            "WHERE date BETWEEN :startDate AND :endDate " +
            "ORDER BY date ASC",
    )
    suspend fun getRange(startDate: String, endDate: String): List<HydrationEntity>

    @Query("DELETE FROM hydration_cache")
    suspend fun clear()
}
