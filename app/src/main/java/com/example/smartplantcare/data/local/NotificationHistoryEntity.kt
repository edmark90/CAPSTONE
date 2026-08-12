package com.example.smartplantcare.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notification_history")
data class NotificationHistoryEntity(
    @PrimaryKey
    val id: String,
    val plantId: String,
    val plantName: String,
    val plantImageUrl: String,
    val day: Int,
    val title: String,
    val message: String,
    val timestamp: Long,
    val isRead: Boolean = false,
    val isCompleted: Boolean = false
)
