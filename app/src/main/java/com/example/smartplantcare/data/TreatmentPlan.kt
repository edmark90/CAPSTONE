package com.example.smartplantcare.data

data class TreatmentPlan(
    val id: String = "",
    val plantName: String = "",
    val diseaseName: String = "",
    val className: String = "",
    val imageUrl: String = "",
    val startDateMillis: Long = System.currentTimeMillis(),
    val sevenDayPlan: List<String> = emptyList(),
    val durations: List<Int> = listOf(10, 10, 20, 10, 15, 5, 10),
    val completedDays: List<Boolean> = List(7) { false },
    val isActive: Boolean = true,
    val currentDayIndex: Int = 0, // Tracks the current active day (0-6)
    val lastNotificationSent: Long = 0L, // Timestamp of last notification sent
    val nextScheduledNotification: Long = 0L, // Timestamp of next scheduled notification
    val lastOverdueCheck: Long = 0L, // Timestamp of last overdue check performed
    val confidence: Float = 0.0f, // ML detection confidence score (0.0 to 1.0)
    val isHealthy: Boolean = false // Whether the plant is healthy or has disease
) {
    // Computed property: the actual current day based on completion status
    val computedCurrentDayIndex: Int
        get() = completedDays.indexOfFirst { !it }.let { if (it == -1) completedDays.size else it }

    val completedCount: Int
        get() = completedDays.count { it }
}