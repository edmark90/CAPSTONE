package com.example.smartplantcare.ui.theme.screens.homescreen

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartplantcare.data.NotificationHistory
import com.example.smartplantcare.data.NotificationHistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
                _notifications.value = repository.getAllNotifications()
                _unreadCount.value = repository.getUnreadCount()
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
                _unreadCount.value = repository.getUnreadCount()
            } catch (e: Exception) {
                Log.e("NotificationHistoryViewModel", "Failed to load unread count", e)
                _unreadCount.value = 0
            }
        }
    }

    fun markAsRead(id: String) {
        viewModelScope.launch {
            try {
                repository.markAsRead(id)
                // Optimize: Update local state instead of reloading from database
                _notifications.value = _notifications.value.map {
                    if (it.id == id) it.copy(isRead = true) else it
                }
                _unreadCount.value = _notifications.value.count { !it.isRead }
            } catch (e: Exception) {
                Log.e("NotificationHistoryViewModel", "Failed to mark as read", e)
            }
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            try {
                repository.markAllAsRead()
                // Optimize: Update local state instead of reloading from database
                _notifications.value = _notifications.value.map { it.copy(isRead = true) }
                _unreadCount.value = 0
            } catch (e: Exception) {
                Log.e("NotificationHistoryViewModel", "Failed to mark all as read", e)
            }
        }
    }

    fun deleteNotification(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteNotification(id)

                _notifications.value = _notifications.value.filter { it.id != id }
                _unreadCount.value = _notifications.value.count { !it.isRead }
            } catch (e: Exception) {
                Log.e("NotificationHistoryViewModel", "Failed to delete notification", e)
            }
        }
    }

    fun deleteAllNotifications() {
        viewModelScope.launch {
            try {
                repository.deleteAllNotifications()
                _notifications.value = emptyList()
                _unreadCount.value = 0
            } catch (e: Exception) {
                Log.e("NotificationHistoryViewModel", "Failed to delete all notifications", e)
            }
        }
    }
}
