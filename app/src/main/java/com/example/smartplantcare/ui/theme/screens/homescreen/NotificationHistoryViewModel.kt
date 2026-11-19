package com.example.smartplantcare.ui.theme.screens.homescreen

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartplantcare.data.NotificationHistory
import com.example.smartplantcare.data.NotificationHistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotificationHistoryViewModel(
    private val context: Context
) : ViewModel() {

    private val repository: NotificationHistoryRepository by lazy {
        try {
            NotificationHistoryRepository(context)
        } catch (e: Exception) {
            Log.e("NotificationHistoryViewModel", "Failed to initialize repository", e)
            throw e
        }
    }

    private val _notifications = MutableStateFlow<List<NotificationHistory>>(emptyList())
    val notifications: StateFlow<List<NotificationHistory>> = _notifications.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadNotifications() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val (items, unread) = withContext(Dispatchers.IO) {
                    repository.getAllNotifications() to repository.getUnreadCount()
                }
                _notifications.value = items
                _unreadCount.value = unread
                _isLoading.value = false
            } catch (e: Exception) {
                Log.e("NotificationHistoryViewModel", "Failed to load notifications", e)
                _isLoading.value = false
            }
        }
    }

    fun loadUnreadCount() {
        viewModelScope.launch {
            try {
                _unreadCount.value = withContext(Dispatchers.IO) { repository.getUnreadCount() }
            } catch (e: Exception) {
                Log.e("NotificationHistoryViewModel", "Failed to load unread count", e)
                _unreadCount.value = 0
            }
        }
    }

    fun markAsRead(id: String) {
        viewModelScope.launch {
            try {
                // Optimistic local UI update for instant zero-lag feedback
                val updated = _notifications.value.map {
                    if (it.id == id) it.copy(isRead = true) else it
                }
                _notifications.value = updated
                _unreadCount.value = updated.count { !it.isRead }
                withContext(Dispatchers.IO) { repository.markAsRead(id) }
            } catch (e: Exception) {
                Log.e("NotificationHistoryViewModel", "Failed to mark as read", e)
            }
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            try {
                _notifications.value = _notifications.value.map { it.copy(isRead = true) }
                _unreadCount.value = 0
                withContext(Dispatchers.IO) { repository.markAllAsRead() }
            } catch (e: Exception) {
                Log.e("NotificationHistoryViewModel", "Failed to mark all as read", e)
            }
        }
    }

    fun deleteNotification(id: String) {
        viewModelScope.launch {
            try {
                val filtered = _notifications.value.filter { it.id != id }
                _notifications.value = filtered
                _unreadCount.value = filtered.count { !it.isRead }
                withContext(Dispatchers.IO) { repository.deleteNotification(id) }
            } catch (e: Exception) {
                Log.e("NotificationHistoryViewModel", "Failed to delete notification", e)
            }
        }
    }

    fun deleteAllNotifications() {
        viewModelScope.launch {
            try {
                _notifications.value = emptyList()
                _unreadCount.value = 0
                withContext(Dispatchers.IO) { repository.deleteAllNotifications() }
            } catch (e: Exception) {
                Log.e("NotificationHistoryViewModel", "Failed to delete all notifications", e)
            }
        }
    }
}
