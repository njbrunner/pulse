package com.pulse.app.health.data.cache.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
internal interface RestingHeartRateDao {

    @Upsert
    suspend fun upsert(entity: RestingHeartRateEntity)

    @Upsert
    suspend fun upsertAll(entities: List<RestingHeartRateEntity>)

    @Query("SELECT * FROM resting_heart_rate_cache WHERE date = :date")
    suspend fun getByDate(date: String): RestingHeartRateEntity?

    @Query(
        "SELECT * FROM resting_heart_rate_cache " +
            "WHERE date BETWEEN :startDate AND :endDate " +
            "ORDER BY date ASC",
    )
    suspend fun getRange(startDate: String, endDate: String): List<RestingHeartRateEntity>

    @Query("DELETE FROM resting_heart_rate_cache")
    suspend fun clear()
}
