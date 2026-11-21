package com.example.smartplantcare.data

import android.content.Context
import android.util.Log
import com.example.smartplantcare.data.local.AppDatabase
import com.example.smartplantcare.data.local.NotificationHistoryDao
import com.example.smartplantcare.data.local.NotificationHistoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Repository for managing NotificationHistory data.
 *
 * All data is saved locally to Room Database (SQLite) as the source of truth.
 */
class NotificationHistoryRepository(
    private val context: Context
) {

    private val notificationDao: NotificationHistoryDao by lazy {
        try {
            AppDatabase.getDatabase(context).notificationHistoryDao()
        } catch (e: Exception) {
            Log.e("NotificationHistoryRepository", "Failed to initialize DAO", e)
            throw e
        }
    }

    suspend fun saveNotification(notification: NotificationHistory): String {
        return try {
            val id = if (notification.id.isBlank()) generateLocalId() else notification.id
            val entity = NotificationHistoryEntity(
                id = id,
                plantId = notification.plantId,
                plantName = notification.plantName,
                plantImageUrl = notification.plantImageUrl,
                day = notification.day,
                title = notification.title,
                message = notification.message,
                timestamp = notification.timestamp,
                isRead = notification.isRead,
                isCompleted = notification.isCompleted
            )
            notificationDao.insertNotification(entity)
            notificationDao.pruneOldNotifications()
            Log.d("NotificationHistoryRepository", "Notification saved to local database: $id")
            id
        } catch (e: Exception) {
            Log.e("NotificationHistoryRepository", "Failed to save notification", e)
            ""
        }
    }

    suspend fun pruneHistory() {
        try {
            notificationDao.pruneOldNotifications()
        } catch (e: Exception) {
            Log.e("NotificationHistoryRepository", "Failed to prune notification history", e)
        }
    }

    suspend fun getAllNotifications(): List<NotificationHistory> {
        return try {
            val entities = notificationDao.getAllNotifications().first()
            entities.map { entity ->
                NotificationHistory(
                    id = entity.id,
                    plantId = entity.plantId,
                    plantName = entity.plantName,
                    plantImageUrl = entity.plantImageUrl,
                    day = entity.day,
                    title = entity.title,
                    message = entity.message,
                    timestamp = entity.timestamp,
                    isRead = entity.isRead,
                    isCompleted = entity.isCompleted
                )
            }
        } catch (e: Exception) {
            Log.e("NotificationHistoryRepository", "Failed to get all notifications", e)
            emptyList()
        }
    }

    suspend fun getUnreadNotifications(): List<NotificationHistory> {
        return try {
            val entities = notificationDao.getUnreadNotifications().first()
            entities.map { entity ->
                NotificationHistory(
                    id = entity.id,
                    plantId = entity.plantId,
                    plantName = entity.plantName,
                    plantImageUrl = entity.plantImageUrl,
                    day = entity.day,
                    title = entity.title,
                    message = entity.message,
                    timestamp = entity.timestamp,
                    isRead = entity.isRead,
                    isCompleted = entity.isCompleted
                )
            }
        } catch (e: Exception) {
            Log.e("NotificationHistoryRepository", "Failed to get unread notifications", e)
            emptyList()
        }
    }

    suspend fun getUnreadCount(): Int {
        return try {
            notificationDao.getUnreadCount()
        } catch (e: Exception) {
            Log.e("NotificationHistoryRepository", "Failed to get unread count", e)
            0
        }
    }

    suspend fun getNotificationById(id: String): NotificationHistory? {
        return try {
            val entity = notificationDao.getNotificationById(id) ?: return null
            NotificationHistory(
                id = entity.id,
                plantId = entity.plantId,
                plantName = entity.plantName,
                plantImageUrl = entity.plantImageUrl,
                day = entity.day,
                title = entity.title,
                message = entity.message,
                timestamp = entity.timestamp,
                isRead = entity.isRead,
                isCompleted = entity.isCompleted
            )
        } catch (e: Exception) {
            Log.e("NotificationHistoryRepository", "Failed to get notification by id", e)
            null
        }
    }

    suspend fun getNotificationsByPlantId(plantId: String): List<NotificationHistory> {
        return try {
            val entities = notificationDao.getNotificationsByPlantId(plantId).first()
            entities.map { entity ->
                NotificationHistory(
                    id = entity.id,
                    plantId = entity.plantId,
                    plantName = entity.plantName,
                    plantImageUrl = entity.plantImageUrl,
                    day = entity.day,
                    title = entity.title,
                    message = entity.message,
                    timestamp = entity.timestamp,
                    isRead = entity.isRead,
                    isCompleted = entity.isCompleted
                )
            }
        } catch (e: Exception) {
            Log.e("NotificationHistoryRepository", "Failed to get notifications by plant id", e)
            emptyList()
        }
    }

    suspend fun markAsRead(id: String) {
        try {
            notificationDao.markAsReadById(id)
            Log.d("NotificationHistoryRepository", "Notification marked as read: $id")
        } catch (e: Exception) {
            Log.e("NotificationHistoryRepository", "Failed to mark as read", e)
        }
    }

    suspend fun markAllAsRead() {
        try {
            notificationDao.markAllAsRead()
            Log.d("NotificationHistoryRepository", "All notifications marked as read")
        } catch (e: Exception) {
            Log.e("NotificationHistoryRepository", "Failed to mark all as read", e)
        }
    }

    suspend fun deleteNotification(id: String) {
        try {
            notificationDao.deleteNotification(id)
            Log.d("NotificationHistoryRepository", "Notification deleted: $id")
        } catch (e: Exception) {
            Log.e("NotificationHistoryRepository", "Failed to delete notification", e)
        }
    }

    suspend fun deleteAllNotifications() {
        try {
            notificationDao.deleteAllNotifications()
            Log.d("NotificationHistoryRepository", "All notifications deleted")
        } catch (e: Exception) {
            Log.e("NotificationHistoryRepository", "Failed to delete all notifications", e)
        }
    }

    private fun generateLocalId(): String {
        return "notif_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }
}
