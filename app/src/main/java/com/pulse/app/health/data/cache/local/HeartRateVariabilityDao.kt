package com.pulse.app.health.data.cache.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
internal interface HeartRateVariabilityDao {

    @Upsert
    suspend fun upsert(entity: HeartRateVariabilityEntity)

    @Upsert
    suspend fun upsertAll(entities: List<HeartRateVariabilityEntity>)

    @Query("SELECT * FROM heart_rate_variability_cache WHERE date = :date")
    suspend fun getByDate(date: String): HeartRateVariabilityEntity?

    @Query(
        "SELECT * FROM heart_rate_variability_cache " +
            "WHERE date BETWEEN :startDate AND :endDate " +
            "ORDER BY date ASC",
    )
    suspend fun getRange(startDate: String, endDate: String): List<HeartRateVariabilityEntity>

    @Query("DELETE FROM heart_rate_variability_cache")
    suspend fun clear()
}
