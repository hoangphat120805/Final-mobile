package com.example.vaiche_driver.data.repository

import android.content.Context
import com.example.vaiche_driver.data.common.ApiProvider
import com.example.vaiche_driver.data.common.safeApiCall
import com.example.vaiche_driver.data.local.SessionManager
import com.example.vaiche_driver.data.network.RetrofitClient
import com.example.vaiche_driver.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository quản lý Xác thực, Người dùng, OTP.
 * - Dùng ApiProvider để luôn lấy ApiService mới nhất (tránh 401 do instance cũ).
 * - Dùng safeApiCall để chuẩn hóa xử lý lỗi/response.
 * - Reset Retrofit sau login/logout để đảm bảo interceptor đọc token mới/đã xóa.
 */
class AuthRepository(
    private val apiProvider: ApiProvider,
    private val sessionManager: SessionManager,
    private val appContext: Context
) {
    private val api get() = apiProvider()

    //==================== AUTH & SESSION ====================

    /**
     * Đăng nhập: lưu token, reset Retrofit để mọi request sau dùng token mới.
     */
    suspend fun login(loginRequest: UserLoginRequest): Result<TokenResponse> =
        withContext(Dispatchers.IO) {
            val result = safeApiCall { api.login(loginRequest) }
            result.onSuccess { tokenRes ->
                sessionManager.saveAuthToken(tokenRes.accessToken)
                // rebuild OkHttp/Retrofit để interceptor đọc token mới
                RetrofitClient.reset(appContext)
            }
            result
        }

    /**
     * Đăng ký collector.
     */
    suspend fun signup(registerRequest: CollectorRegisterRequest): Result<UserPublic> =
        withContext(Dispatchers.IO) {
            safeApiCall { api.signup(registerRequest) }
        }

    /**
     * Kiểm tra đã đăng nhập (có token local).
     */
    fun isUserLoggedIn(): Boolean = sessionManager.fetchAuthToken() != null

    /**
     * Đăng xuất: xoá token, reset Retrofit để ngắt Authorization header ngay.
     */
    fun logout(): Result<Unit> = try {
        sessionManager.clearAuthToken()
        RetrofitClient.reset(appContext)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    //==================== OTP ====================

    suspend fun sendOtp(otpRequest: OtpRequest): Result<Unit> =
        withContext(Dispatchers.IO) {
            // backend trả MessageResponse/Unit tuỳ API;
            // ở đây chỉ cần status 2xx là OK -> map sang Unit bằng safeApiCall
            safeApiCall { api.sendOtp(otpRequest) }.mapToUnit()
        }

    suspend fun verifyOtp(otpVerifyRequest: OtpVerifyRequest): Result<OtpVerifyResponse> =
        withContext(Dispatchers.IO) {
            safeApiCall { api.verifyOtp(otpVerifyRequest) }
        }

    //==================== PASSWORD RESET ====================

    suspend fun resetPassword(resetPasswordRequest: ResetPasswordRequest): Result<Unit> =
        withContext(Dispatchers.IO) {
            safeApiCall { api.resetPassword(resetPasswordRequest) }.mapToUnit()
        }

    // --- helpers ---
    private fun <T> Result<T>.mapToUnit(): Result<Unit> =
        fold(onSuccess = { Result.success(Unit) }, onFailure = { Result.failure(it) })

    companion object {
        @Volatile private var instance: AuthRepository? = null

        /**
         * Gợi ý khởi tạo:
         * - ApiProvider dùng `RetrofitClient.instance` (đã `init` trong Application.onCreate).
         * - Lưu appContext để gọi `RetrofitClient.reset(appContext)` khi login/logout.
         */
        fun getInstance(context: Context): AuthRepository {
            val appCtx = context.applicationContext
            return instance ?: synchronized(this) {
                instance ?: AuthRepository(
                    apiProvider = { RetrofitClient.instance }, // luôn lấy instance hiện tại
                    sessionManager = SessionManager(appCtx),
                    appContext = appCtx
                ).also { instance = it }
            }
        }
    }
}
