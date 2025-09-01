package com.example.vaicheuserapp.data.model

import com.google.gson.annotations.SerializedName

// For POST /api/otp/send-otp
data class OTPRequest(
    val email: String,
    val purpose: String // "reset"
)

// For POST /api/otp/verify-otp
data class OTPVerifyRequest(
    val email: String,
    val otp: String,
    val purpose: String // "reset"
)

// For POST /api/user/reset-password
// This matches Body_reset_password_api_user_reset_password_post
data class ResetPasswordRequest(
    val email: String,
    @SerializedName("reset_token")
    val resetToken: String,
    @SerializedName("new_password")
    val newPassword: String
)

data class OTPVerifyResponse(
    @SerializedName("reset_token")
    val resetToken: String
)

// For OTP Purpose enum (as per spec)
enum class OTPPurpose {
    @SerializedName("register")
    register,
    @SerializedName("reset")
    reset
}