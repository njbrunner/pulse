package com.pulse.app.health.data.cache.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SleepEntity::class,
        StepsEntity::class,
        RestingHeartRateEntity::class,
        HeartRateVariabilityEntity::class,
        HydrationEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
internal abstract class PulseHealthDatabase : RoomDatabase() {

    abstract fun sleepDao(): SleepDao
    abstract fun stepsDao(): StepsDao
    abstract fun restingHeartRateDao(): RestingHeartRateDao
    abstract fun heartRateVariabilityDao(): HeartRateVariabilityDao
    abstract fun hydrationDao(): HydrationDao

    companion object {
        fun create(context: Context): PulseHealthDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                PulseHealthDatabase::class.java,
                "pulse_health_database",
            ).build()
        }

        fun createInMemory(context: Context): PulseHealthDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                PulseHealthDatabase::class.java,
            ).build()
        }
    }
}
