package com.example.vaiche_driver.data.repository

import com.example.vaiche_driver.data.common.ApiProvider
import com.example.vaiche_driver.data.common.safeApiCall
import com.example.vaiche_driver.model.UserNotification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NotificationRepository(
    private val apiProvider: ApiProvider
) {
    private val api get() = apiProvider()

    suspend fun getNotifications(): Result<List<UserNotification>> = withContext(Dispatchers.IO) {
        safeApiCall { api.getMyNotifications() }
    }

    suspend fun markAsRead(notificationId: String): Result<Unit> = withContext(Dispatchers.IO) {
        safeApiCall {
            api.markNotificationAsRead(notificationId)
        }
    }
}