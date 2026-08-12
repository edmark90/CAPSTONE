package com.example.smartplantcare.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.concurrent.Executors

@Database(
    entities = [TreatmentPlanEntity::class, NotificationHistoryEntity::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun treatmentPlanDao(): TreatmentPlanDao
    abstract fun notificationHistoryDao(): NotificationHistoryDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        // Migration from version 1 to 2: Add new fields for sequential monitoring
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add new columns with default values
                database.execSQL(
                    "ALTER TABLE treatment_plans ADD COLUMN currentDayIndex INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE treatment_plans ADD COLUMN lastNotificationSent INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE treatment_plans ADD COLUMN nextScheduledNotification INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE treatment_plans ADD COLUMN lastOverdueCheck INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        // Migration from version 2 to 3: Add confidence and isHealthy fields
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add new columns with default values
                database.execSQL(
                    "ALTER TABLE treatment_plans ADD COLUMN confidence REAL NOT NULL DEFAULT 0.0"
                )
                database.execSQL(
                    "ALTER TABLE treatment_plans ADD COLUMN isHealthy INTEGER NOT NULL DEFAULT 0"
                )
            }
        }


        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS notification_history (
                        id TEXT PRIMARY KEY NOT NULL,
                        plantId TEXT NOT NULL,
                        plantName TEXT NOT NULL,
                        plantImageUrl TEXT NOT NULL,
                        day INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        message TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        isRead INTEGER NOT NULL DEFAULT 0,
                        isCompleted INTEGER NOT NULL DEFAULT 0
                    )"""
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smartplantcare_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigrationOnDowngrade()
                .setQueryExecutor(Executors.newFixedThreadPool(4))
                .setTransactionExecutor(Executors.newFixedThreadPool(4))
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
