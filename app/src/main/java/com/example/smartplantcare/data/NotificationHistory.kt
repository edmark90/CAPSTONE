package com.example.smartplantcare.data

data class NotificationHistory(
    val id: String = "",
    val plantId: String = "",
    val plantName: String = "",
    val plantImageUrl: String = "",
    val day: Int = 0,
    val title: String = "",
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val isCompleted: Boolean = false
)
