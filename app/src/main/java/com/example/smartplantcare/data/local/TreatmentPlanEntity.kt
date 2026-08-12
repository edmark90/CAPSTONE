package com.example.smartplantcare.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "treatment_plans")
@TypeConverters(Converters::class)
data class TreatmentPlanEntity(
    @PrimaryKey
    val id: String,
    val plantName: String,
    val diseaseName: String,
    val className: String,
    val imageUrl: String,
    val startDateMillis: Long,
    val sevenDayPlan: List<String>,
    val durations: List<Int>,
    val completedDays: List<Boolean>,
    val isActive: Boolean,
    val currentDayIndex: Int = 0, // Tracks the current active day (0-6)
    val lastNotificationSent: Long = 0L, // Timestamp of last notification sent
    val nextScheduledNotification: Long = 0L, // Timestamp of next scheduled notification
    val lastOverdueCheck: Long = 0L, // Timestamp of last overdue check performed
    val confidence: Float = 0.0f, // ML detection confidence score (0.0 to 1.0)
    val isHealthy: Boolean = false // Whether the plant is healthy or has disease
)
