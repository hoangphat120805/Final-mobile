package com.example.vaicheuserapp.data.network

import com.example.vaicheuserapp.data.model.LoginResponse
import com.example.vaicheuserapp.data.model.UserPublic
import com.example.vaicheuserapp.data.model.CategoryPublic
import com.example.vaicheuserapp.data.model.UserUpdateRequest
import com.example.vaicheuserapp.data.model.UserUpdatePasswordRequest
import com.example.vaicheuserapp.data.model.Message
import com.example.vaicheuserapp.data.model.NotificationPublic
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import com.example.vaicheuserapp.data.model.UploadResponse
import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.Part
import retrofit2.http.Path
import com.example.vaicheuserapp.data.model.OTPRequest // <-- New import
import com.example.vaicheuserapp.data.model.OTPVerificationTokenResponse
import com.example.vaicheuserapp.data.model.OTPVerifyRequest // <-- New import
import com.example.vaicheuserapp.data.model.ResetPasswordRequest // <-- New import
import com.example.vaicheuserapp.data.model.UserRegisterRequest // <-- NEW: For signup request

interface ApiService {

    @POST("/api/auth/signup") // Confirmed endpoint
    suspend fun signup(@Body userRegisterRequest: UserRegisterRequest): Response<UserPublic> // <-- Changed parameter type

    @FormUrlEncoded
    @POST("/api/auth/login/access-token")
    suspend fun login(
        @Field("username") phoneNumber: String, // The API expects 'username' for the phone number
        @Field("password") password: String,
        @Field("grant_type") grantType: String = "password" // Default value as per OAuth2
    ): Response<LoginResponse>

    @GET("/api/category/")
    suspend fun getCategories(): Response<List<CategoryPublic>>

    @GET("/api/user/me")
    suspend fun getUserMe(): Response<UserPublic>

    @PATCH("/api/user/me")
    suspend fun updateUserMe(@Body userUpdateRequest: UserUpdateRequest): Response<UserPublic>

    @PATCH("/api/user/me/password")
    suspend fun updatePassword(@Body userUpdatePasswordRequest: UserUpdatePasswordRequest): Response<Message>

    @Multipart
    @POST("/api/user/upload/avatar") // <--- CORRECTED PATH HERE!
    suspend fun uploadAvatar(@Part file: MultipartBody.Part): Response<Message>

    @GET("/api/user/me/notifications")
    suspend fun getNotifications(): Response<List<NotificationPublic>>

    @POST("/api/user/read/{notification_id}")
    suspend fun markNotificationAsRead(@Path("notification_id") notificationId: String): Response<Message>

    @POST("/api/otp/send-otp")
    suspend fun sendOtp(@Body otpRequest: OTPRequest): Response<Unit> // Returns 200 OK with empty body

    @POST("/api/otp/verify-otp")
    suspend fun verifyOtp(@Body otpVerifyRequest: OTPVerifyRequest): Response<OTPVerificationTokenResponse>
    // --- NEW: Reset Password Endpoint ---
    @POST("/api/user/reset-password")
    suspend fun resetPassword(@Body resetPasswordRequest: ResetPasswordRequest): Response<Unit> // Returns 200 OK with empty
}