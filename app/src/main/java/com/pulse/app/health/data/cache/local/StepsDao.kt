package com.pulse.app.health.data.cache.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
internal interface StepsDao {

    @Upsert
    suspend fun upsert(entity: StepsEntity)

    @Upsert
    suspend fun upsertAll(entities: List<StepsEntity>)

    @Query("SELECT * FROM steps_cache WHERE date = :date")
    suspend fun getByDate(date: String): StepsEntity?

    @Query(
        "SELECT * FROM steps_cache " +
            "WHERE date BETWEEN :startDate AND :endDate " +
            "ORDER BY date ASC",
    )
    suspend fun getRange(startDate: String, endDate: String): List<StepsEntity>

    @Query("DELETE FROM steps_cache")
    suspend fun clear()
}
