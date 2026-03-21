package com.syncu.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.Instant
import java.time.LocalDate

/**
 * Room Database - Version 13: Removed lean mass and bone mass columns for incremental schema cleanup
 */
@Database(
    entities = [Activity::class, SleepSession::class, DailyWellnessRecord::class, IntervalsWellnessRecord::class, CoachWattsWellnessRecord::class],
    version = 13,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun activityDao(): ActivityDao
    abstract fun sleepDao(): SleepDao
    abstract fun wellnessDao(): DailyWellnessDao
    abstract fun intervalsDao(): IntervalsWellnessDao
    abstract fun coachWattsDao(): CoachWattsWellnessDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE activities ADD COLUMN manuallyEdited INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE activities ADD COLUMN lastEditedAt INTEGER")
                database.execSQL("ALTER TABLE activities ADD COLUMN sourceInfo TEXT")
                database.execSQL("ALTER TABLE sleep_sessions ADD COLUMN sourceInfo TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE sleep_sessions ADD COLUMN stageIntervals TEXT")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE sleep_sessions ADD COLUMN asleepDurationMinutes INTEGER")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS daily_wellness_records (
                        date TEXT PRIMARY KEY NOT NULL,
                        steps INTEGER,
                        distanceMeters REAL,
                        caloriesBurned REAL,
                        activeMinutes INTEGER,
                        restingHR INTEGER,
                        avgHR INTEGER,
                        maxHR INTEGER,
                        hrvMs REAL,
                        weightKg REAL,
                        bodyFatPercentage REAL,
                        leanBodyMassKg REAL,
                        boneMassKg REAL,
                        spo2Percentage REAL,
                        glucoseMmol REAL,
                        systolicBP INTEGER,
                        diastolicBP INTEGER,
                        vo2Max REAL,
                        respiratoryRate REAL,
                        lastUpdated INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS intervals_wellness_records (
                        date TEXT PRIMARY KEY NOT NULL,
                        weight REAL,
                        restingHR INTEGER,
                        hrv REAL,
                        kcalConsumed INTEGER,
                        sleepSecs INTEGER,
                        avgSleepingHR REAL,
                        spO2 REAL,
                        systolic INTEGER,
                        diastolic INTEGER,
                        bloodGlucose REAL,
                        bodyFat REAL,
                        leanMass REAL,
                        boneMass REAL,
                        vo2max REAL,
                        steps INTEGER,
                        respiration REAL,
                        carbohydrates REAL,
                        protein REAL,
                        fatTotal REAL,
                        lastUpdated INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE intervals_wellness_records ADD COLUMN lastSyncedAt INTEGER")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS coachwatts_wellness_records (
                        date TEXT PRIMARY KEY NOT NULL,
                        weight REAL,
                        restingHR INTEGER,
                        hrv REAL,
                        sleepMinutes INTEGER,
                        spO2 REAL,
                        systolic INTEGER,
                        diastolic INTEGER,
                        glucose REAL,
                        bodyFat REAL,
                        leanMass REAL,
                        boneMass REAL,
                        vo2max REAL,
                        steps INTEGER,
                        respiration REAL,
                        calories REAL,
                        carbs REAL,
                        protein REAL,
                        fat REAL,
                        lastUpdated INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE coachwatts_wellness_records ADD COLUMN lastSyncedAt INTEGER")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE coachwatts_wellness_records ADD COLUMN avgSleepingHR REAL")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE coachwatts_wellness_records ADD COLUMN totalCalories REAL")
            }
        }

        // Migration 12 -> 13: Remove lean mass and bone mass columns from all tables
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. daily_wellness_records
                database.execSQL("CREATE TABLE daily_wellness_records_new (date TEXT PRIMARY KEY NOT NULL, steps INTEGER, caloriesBurned REAL, activeMinutes INTEGER, restingHR INTEGER, maxHR INTEGER, hrvMs REAL, weightKg REAL, bodyFatPercentage REAL, spo2Percentage REAL, glucoseMmol REAL, systolicBP INTEGER, diastolicBP INTEGER, vo2Max REAL, respiratoryRate REAL, lastUpdated INTEGER NOT NULL)")
                database.execSQL("INSERT INTO daily_wellness_records_new (date, steps, caloriesBurned, activeMinutes, restingHR, maxHR, hrvMs, weightKg, bodyFatPercentage, spo2Percentage, glucoseMmol, systolicBP, diastolicBP, vo2Max, respiratoryRate, lastUpdated) SELECT date, steps, caloriesBurned, activeMinutes, restingHR, maxHR, hrvMs, weightKg, bodyFatPercentage, spo2Percentage, glucoseMmol, systolicBP, diastolicBP, vo2Max, respiratoryRate, lastUpdated FROM daily_wellness_records")
                database.execSQL("DROP TABLE daily_wellness_records")
                database.execSQL("ALTER TABLE daily_wellness_records_new RENAME TO daily_wellness_records")

                // 2. intervals_wellness_records
                database.execSQL("CREATE TABLE intervals_wellness_records_new (date TEXT PRIMARY KEY NOT NULL, weight REAL, restingHR INTEGER, hrv REAL, kcalConsumed INTEGER, sleepSecs INTEGER, avgSleepingHR REAL, spO2 REAL, systolic INTEGER, diastolic INTEGER, bloodGlucose REAL, bodyFat REAL, vo2max REAL, steps INTEGER, respiration REAL, carbohydrates REAL, protein REAL, fatTotal REAL, lastUpdated INTEGER NOT NULL, lastSyncedAt INTEGER)")
                database.execSQL("INSERT INTO intervals_wellness_records_new (date, weight, restingHR, hrv, kcalConsumed, sleepSecs, avgSleepingHR, spO2, systolic, diastolic, bloodGlucose, bodyFat, vo2max, steps, respiration, carbohydrates, protein, fatTotal, lastUpdated, lastSyncedAt) SELECT date, weight, restingHR, hrv, kcalConsumed, sleepSecs, avgSleepingHR, spO2, systolic, diastolic, bloodGlucose, bodyFat, vo2max, steps, respiration, carbohydrates, protein, fatTotal, lastUpdated, lastSyncedAt FROM intervals_wellness_records")
                database.execSQL("DROP TABLE intervals_wellness_records")
                database.execSQL("ALTER TABLE intervals_wellness_records_new RENAME TO intervals_wellness_records")

                // 3. coachwatts_wellness_records
                database.execSQL("CREATE TABLE coachwatts_wellness_records_new (date TEXT PRIMARY KEY NOT NULL, weight REAL, restingHR INTEGER, hrv REAL, sleepMinutes INTEGER, avgSleepingHR REAL, spO2 REAL, systolic INTEGER, diastolic INTEGER, glucose REAL, bodyFat REAL, vo2max REAL, steps INTEGER, respiration REAL, calories REAL, totalCalories REAL, carbs REAL, protein REAL, fat REAL, lastUpdated INTEGER NOT NULL, lastSyncedAt INTEGER)")
                database.execSQL("INSERT INTO coachwatts_wellness_records_new (date, weight, restingHR, hrv, sleepMinutes, avgSleepingHR, spO2, systolic, diastolic, glucose, bodyFat, vo2max, steps, respiration, calories, totalCalories, carbs, protein, fat, lastUpdated, lastSyncedAt) SELECT date, weight, restingHR, hrv, sleepMinutes, avgSleepingHR, spO2, systolic, diastolic, glucose, bodyFat, vo2max, steps, respiration, calories, totalCalories, carbs, protein, fat, lastUpdated, lastSyncedAt FROM coachwatts_wellness_records")
                database.execSQL("DROP TABLE coachwatts_wellness_records")
                database.execSQL("ALTER TABLE coachwatts_wellness_records_new RENAME TO coachwatts_wellness_records")
            }
        }

        fun getDatabase(context: Context, passphrase: CharArray): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "syncu_database"
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, 
                        MIGRATION_5_6, MIGRATION_6_7, MIGRATION_8_9, MIGRATION_9_10, 
                        MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13
                    )
                    .fallbackToDestructiveMigration()
                    .build()
                
                INSTANCE = instance
                instance
            }
        }
    }
}

class Converters {
    @androidx.room.TypeConverter
    fun fromTimestamp(value: Long?): Instant? {
        return value?.let { Instant.ofEpochMilli(it) }
    }

    @androidx.room.TypeConverter
    fun toTimestamp(instant: Instant?): Long? {
        return instant?.toEpochMilli()
    }

    @androidx.room.TypeConverter
    fun fromLocalDate(value: String?): LocalDate? {
        return value?.let { LocalDate.parse(it) }
    }

    @androidx.room.TypeConverter
    fun toLocalDate(date: LocalDate?): String? {
        return date?.toString()
    }

    @androidx.room.TypeConverter
    fun fromActivityType(value: ActivityType): String {
        return value.name
    }

    @androidx.room.TypeConverter
    fun toActivityType(value: String): ActivityType {
        return ActivityType.valueOf(value)
    }
}

@Dao
interface DailyWellnessDao {
    @Query("SELECT * FROM daily_wellness_records WHERE date = :date")
    suspend fun getWellnessForDate(date: LocalDate): DailyWellnessRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWellness(record: DailyWellnessRecord)

    @Query("DELETE FROM daily_wellness_records WHERE date = :date")
    suspend fun deleteWellnessForDate(date: LocalDate)

    @Query("DELETE FROM daily_wellness_records WHERE date < :cutoffDate")
    suspend fun deleteWellnessBefore(cutoffDate: LocalDate)
}

@Dao
interface IntervalsWellnessDao {
    @Query("SELECT * FROM intervals_wellness_records WHERE date = :date")
    suspend fun getWellnessForDate(date: LocalDate): IntervalsWellnessRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWellness(record: IntervalsWellnessRecord)

    @Query("DELETE FROM intervals_wellness_records WHERE date < :cutoffDate")
    suspend fun deleteWellnessBefore(cutoffDate: LocalDate)
    
    @Query("UPDATE intervals_wellness_records SET lastSyncedAt = :timestamp WHERE date = :date")
    suspend fun updateLastSyncedAt(date: LocalDate, timestamp: Instant)
}

@Dao
interface CoachWattsWellnessDao {
    @Query("SELECT * FROM coachwatts_wellness_records WHERE date = :date")
    suspend fun getWellnessForDate(date: LocalDate): CoachWattsWellnessRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWellness(record: CoachWattsWellnessRecord)

    @Query("DELETE FROM coachwatts_wellness_records WHERE date < :cutoffDate")
    suspend fun deleteWellnessBefore(cutoffDate: LocalDate)

    @Query("UPDATE coachwatts_wellness_records SET lastSyncedAt = :timestamp WHERE date = :date")
    suspend fun updateLastSyncedAt(date: LocalDate, timestamp: Instant)
}

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activities WHERE startTime >= :startDate AND startTime < :endDate ORDER BY startTime DESC")
    suspend fun getActivitiesForDateRange(startDate: Long, endDate: Long): List<Activity>
    
    @Query("SELECT * FROM activities WHERE synced = 0")
    suspend fun getUnsyncedActivities(): List<Activity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivity(activity: Activity)
    
    @Update
    suspend fun updateActivity(activity: Activity)
    
    @Query("UPDATE activities SET synced = 1, syncedAt = :syncedAt WHERE id = :id")
    suspend fun markAsSynced(id: String, syncedAt: Instant)
    
    @Query("DELETE FROM activities WHERE id = :id")
    suspend fun deleteActivity(id: String)
    
    @Query("SELECT * FROM activities WHERE id = :id")
    suspend fun getActivityById(id: String): Activity?
}

@Dao
interface SleepDao {
    @Query("SELECT * FROM sleep_sessions WHERE endTime >= :startDate AND endTime < :endDate ORDER BY endTime DESC")
    suspend fun getSleepForDateRange(startDate: Long, endDate: Long): List<SleepSession>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSleep(sleep: SleepSession)
    
    @Query("DELETE FROM sleep_sessions WHERE id = :id")
    suspend fun deleteSleep(id: String)
}
