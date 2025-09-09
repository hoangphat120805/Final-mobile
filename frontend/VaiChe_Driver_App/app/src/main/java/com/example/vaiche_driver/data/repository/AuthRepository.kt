package com.example.vaiche_driver.data.repository

import android.content.Context
import com.example.vaiche_driver.data.local.SessionManager
import com.example.vaiche_driver.data.network.ApiService
import com.example.vaiche_driver.data.network.RetrofitClient
import com.example.vaiche_driver.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository quản lý tất cả các hoạt động liên quan đến Xác thực, Người dùng, và OTP.
 * Nó là lớp trung gian duy nhất giữa AuthViewModel và các nguồn dữ liệu.
 */
class AuthRepository(
    private val apiService: ApiService,
    private val sessionManager: SessionManager
) {

    //==================== AUTH & SESSION ====================

    /**
     * Thực hiện đăng nhập.
     * Nếu thành công, lưu token vào SessionManager.
     */
    suspend fun login(loginRequest: UserLoginRequest): Result<TokenResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.login(loginRequest)
                if (response.isSuccessful && response.body() != null) {
                    sessionManager.saveAuthToken(response.body()!!.accessToken)
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Login failed: ${response.code()} - ${response.message()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Thực hiện đăng ký tài khoản collector.
     */
    suspend fun signup(registerRequest: CollectorRegisterRequest): Result<UserPublic> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.signup(registerRequest)
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    val err = response.errorBody()?.string()
                    Result.failure(Exception("Signup failed: ${response.code()} - ${err ?: response.message()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Kiểm tra xem người dùng đã đăng nhập hay chưa bằng cách kiểm tra token.
     */
    fun isUserLoggedIn(): Boolean {
        return sessionManager.fetchAuthToken() != null
    }

    /**
     * Thực hiện đăng xuất bằng cách xóa token đã lưu.
     */
    fun logout() {
        sessionManager.clearAuthToken()
    }

    //==================== OTP ====================

    /**
     * Yêu cầu gửi mã OTP đến email.
     */
    suspend fun sendOtp(otpRequest: OtpRequest): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.sendOtp(otpRequest)
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to send OTP: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Xác thực mã OTP.
     */
    suspend fun verifyOtp(otpVerifyRequest: OtpVerifyRequest): Result<OtpVerifyResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.verifyOtp(otpVerifyRequest)
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("OTP verification failed: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    //==================== PASSWORD RESET ====================

    /**
     * Đặt lại mật khẩu bằng token đã xác thực.
     */
    suspend fun resetPassword(resetPasswordRequest: ResetPasswordRequest): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.resetPassword(resetPasswordRequest)
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to reset password: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Companion object để tạo instance của Repository một cách dễ dàng (Singleton).
     * Nó sẽ tự động lấy ApiService và tạo SessionManager.
     */
    companion object {
        @Volatile
        private var instance: AuthRepository? = null

        fun getInstance(context: Context): AuthRepository {
            return instance ?: synchronized(this) {
                instance ?: AuthRepository(
                    apiService = RetrofitClient.getInstance(context),
                    sessionManager = SessionManager(context)
                ).also { instance = it }
            }
        }
    }
}