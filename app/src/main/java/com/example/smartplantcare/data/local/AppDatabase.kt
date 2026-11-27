package com.example.smartplantcare.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.concurrent.Executors

@Database(
    entities = [TreatmentPlanEntity::class, NotificationHistoryEntity::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun treatmentPlanDao(): TreatmentPlanDao
    abstract fun notificationHistoryDao(): NotificationHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migration from version 1 to 2: Add legacy fields for sequential monitoring
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
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
        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE treatment_plans ADD COLUMN confidence REAL NOT NULL DEFAULT 0.0"
                )
                database.execSQL(
                    "ALTER TABLE treatment_plans ADD COLUMN isHealthy INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        // Migration from version 3 to 4: Add notification_history table
        internal val MIGRATION_3_4 = object : Migration(3, 4) {
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

        // Migration from version 4 to 5: Preserve all saved plant records while dropping
        // deprecated 7-day treatment schedule, checklist, and reminder columns.
        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS treatment_plans_new (
                        id TEXT PRIMARY KEY NOT NULL,
                        plantName TEXT NOT NULL,
                        diseaseName TEXT NOT NULL,
                        className TEXT NOT NULL,
                        imageUrl TEXT NOT NULL,
                        startDateMillis INTEGER NOT NULL,
                        confidence REAL NOT NULL DEFAULT 0.0,
                        isHealthy INTEGER NOT NULL DEFAULT 0
                    )"""
                )
                database.execSQL(
                    """INSERT INTO treatment_plans_new (
                        id, plantName, diseaseName, className, imageUrl, startDateMillis, confidence, isHealthy
                    )
                    SELECT
                        id, plantName, diseaseName, className, imageUrl, startDateMillis, confidence, isHealthy
                    FROM treatment_plans"""
                )
                database.execSQL("DROP TABLE treatment_plans")
                database.execSQL("ALTER TABLE treatment_plans_new RENAME TO treatment_plans")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smartplantcare_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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
