package com.pulse.app.health.data.cache.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SleepEntity::class,
        StepsEntity::class,
        RestingHeartRateEntity::class,
        HeartRateVariabilityEntity::class,
        HydrationEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
internal abstract class PulseHealthDatabase : RoomDatabase() {

    abstract fun sleepDao(): SleepDao
    abstract fun stepsDao(): StepsDao
    abstract fun restingHeartRateDao(): RestingHeartRateDao
    abstract fun heartRateVariabilityDao(): HeartRateVariabilityDao
    abstract fun hydrationDao(): HydrationDao

    companion object {

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE sleep_cache ADD COLUMN primary_session_start_millis INTEGER",
                )
                db.execSQL(
                    "ALTER TABLE sleep_cache ADD COLUMN primary_session_end_millis INTEGER",
                )
            }
        }

        fun create(context: Context): PulseHealthDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                PulseHealthDatabase::class.java,
                "pulse_health_database",
            )
                .addMigrations(MIGRATION_1_2)
                .build()
        }

        fun createInMemory(context: Context): PulseHealthDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                PulseHealthDatabase::class.java,
            ).build()
        }
    }
}
