package com.example.vaiche_driver.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vaiche_driver.data.network.RetrofitClient
import com.example.vaiche_driver.data.repository.NotificationRepository
import com.example.vaiche_driver.model.UserNotification
import com.example.vaiche_driver.model.UserStats
import kotlinx.coroutines.launch

class NotificationViewModel(
    private val repo: NotificationRepository = NotificationRepository { RetrofitClient.instance }
) : ViewModel() {

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _notifications = MutableLiveData<List<UserNotification>>(emptyList())
    val notifications: LiveData<List<UserNotification>> = _notifications

    private val _error = MutableLiveData<Event<String>>()
    val error: LiveData<Event<String>> = _error

    fun load() {
        if (_notifications.value?.isNotEmpty() == true) return
        _isLoading.value = true
        viewModelScope.launch {
            repo.getNotifications()
                .onSuccess {
                    _notifications.value = it
                }
                .onFailure { e ->
                    _error.value = Event(e.message ?: "Load notifications failed")
                }
            _isLoading.value = false
        }
    }

    fun markAsRead(notificationId: String) {
        viewModelScope.launch {
            repo.markAsRead(notificationId)
                .onSuccess {
                    _notifications.value = _notifications.value?.map {
                        if (it.id == notificationId) it.copy(isRead = true) else it
                    }
                }
                .onFailure { e ->
                    _error.value = Event(e.message ?: "Mark as read failed")
                }
        }
    }
}