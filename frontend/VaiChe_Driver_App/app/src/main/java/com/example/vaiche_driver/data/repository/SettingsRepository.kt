package com.example.vaiche_driver.data.repository

import com.example.vaiche_driver.data.local.SessionManager
import com.example.vaiche_driver.data.network.ApiService
import com.example.vaiche_driver.data.network.RetrofitClient
import com.example.vaiche_driver.model.MessageResponse
import com.example.vaiche_driver.model.UpdatePassword
import com.example.vaiche_driver.model.UpdateProfileRequest
import com.example.vaiche_driver.model.UserPublic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody

class SettingsRepository(
    private val api: ApiService = RetrofitClient.instance,
    private val sessionManager: SessionManager
) {

    suspend fun updatePassword(body: UpdatePassword): Result<MessageResponse> = withContext(Dispatchers.IO) {
        try {
            val res = api.updatePassword(body)
            if (res.isSuccessful && res.body() != null) Result.success(res.body()!!)
            else Result.failure(Exception("Update password error: ${res.code()}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun uploadAvatar(file: MultipartBody.Part): Result<MessageResponse> = withContext(Dispatchers.IO) {
        try {
            val res = api.uploadAvatar(file)
            if (res.isSuccessful && res.body() != null) Result.success(res.body()!!)
            else Result.failure(Exception("Upload avatar error: ${res.code()}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun updateProfile(body: UpdateProfileRequest): Result<UserPublic> = withContext(Dispatchers.IO) {
        try {
            val res = api.updateMyProfile(body)
            if (res.isSuccessful && res.body() != null) Result.success(res.body()!!)
            else Result.failure(Exception("Update profile error: ${res.code()}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    /** Xoá token khi logout */
    fun logout(): Result<Unit> {
        return try {
            sessionManager.clearAuthToken()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
