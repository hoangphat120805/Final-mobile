package com.example.vaiche_driver.data.repository

import com.example.vaiche_driver.data.common.safeApiCall
import com.example.vaiche_driver.data.local.SessionManager
import com.example.vaiche_driver.data.network.ApiService
import com.example.vaiche_driver.model.MessageResponse
import com.example.vaiche_driver.model.UpdatePassword
import com.example.vaiche_driver.model.UpdateProfileRequest
import com.example.vaiche_driver.model.UserPublic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import org.json.JSONObject
import retrofit2.Response

// Gợi ý: đặt file common/ApiProvider.kt
// typealias ApiProvider = () -> ApiService

class SettingsRepository(
    private val apiProvider: () -> ApiService,
    private val sessionManager: SessionManager
) {

    private val api get() = apiProvider()

    // ---- APIs ----

    suspend fun updatePassword(body: UpdatePassword): Result<MessageResponse> =
        withContext(Dispatchers.IO) { safeApiCall { api.updatePassword(body) } }

    suspend fun uploadAvatar(file: MultipartBody.Part): Result<MessageResponse> =
        withContext(Dispatchers.IO) { safeApiCall { api.uploadAvatar(file) } }

    suspend fun updateProfile(body: UpdateProfileRequest): Result<UserPublic> =
        withContext(Dispatchers.IO) {
            val result = safeApiCall { api.updateMyProfile(body) }
            result.onSuccess { user ->
                // Nếu có nhu cầu, lưu thêm vào SessionManager tại đây.
                // sessionManager.saveUser(user)
            }
            result
        }
}
