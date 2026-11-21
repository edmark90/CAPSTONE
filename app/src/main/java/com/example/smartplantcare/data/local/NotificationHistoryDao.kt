package com.example.smartplantcare.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationHistoryDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationHistoryEntity)
    
    @Query("SELECT * FROM notification_history ORDER BY timestamp DESC LIMIT 50")
    fun getAllNotifications(): Flow<List<NotificationHistoryEntity>>
    
    @Query("SELECT * FROM notification_history WHERE isRead = 0 ORDER BY timestamp DESC LIMIT 50")
    fun getUnreadNotifications(): Flow<List<NotificationHistoryEntity>>
    
    @Query("DELETE FROM notification_history WHERE id NOT IN (SELECT id FROM notification_history ORDER BY timestamp DESC LIMIT 50)")
    suspend fun pruneOldNotifications()
    
    @Query("SELECT COUNT(*) FROM notification_history WHERE isRead = 0")
    suspend fun getUnreadCount(): Int
    
    @Query("SELECT * FROM notification_history WHERE id = :id LIMIT 1")
    suspend fun getNotificationById(id: String): NotificationHistoryEntity?
    
    @Query("SELECT * FROM notification_history WHERE plantId = :plantId ORDER BY timestamp DESC")
    fun getNotificationsByPlantId(plantId: String): Flow<List<NotificationHistoryEntity>>
    
    @Update
    suspend fun markAsRead(notification: NotificationHistoryEntity)
    
    @Query("UPDATE notification_history SET isRead = 1 WHERE id = :id")
    suspend fun markAsReadById(id: String)
    
    @Query("UPDATE notification_history SET isRead = 1")
    suspend fun markAllAsRead()
    
    @Query("DELETE FROM notification_history WHERE id = :id")
    suspend fun deleteNotification(id: String)
    
    @Query("DELETE FROM notification_history")
    suspend fun deleteAllNotifications()
}
