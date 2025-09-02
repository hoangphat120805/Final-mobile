package com.example.vaicheuserapp.data.model

import com.google.gson.annotations.SerializedName
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize // Ensure this is present
data class UserRegisterRequest(
    val email: String,
    @SerializedName("phone_number")
    val phoneNumber: String,
    val password: String,
    @SerializedName("full_name")
    val fullName: String,
    @SerializedName("register_token")
    val registerToken: String
) : Parcelable

// ResetPasswordRequest and OTPPurpose enum - no change
data class ResetPasswordRequest(
    val email: String,
    @SerializedName("reset_token")
    val resetToken: String,
    @SerializedName("new_password")
    val newPassword: String
)

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

data class OTPVerificationTokenResponse( // Renamed from RegisterTokenResponse
    @SerializedName("token") // <--- Use "reset_token" as per backend's actual response
    val verificationToken: String // <--- Generic name for the token
)

// For OTP Purpose enum (as per spec)
enum class OTPPurpose {
    @SerializedName("register")
    register,
    @SerializedName("reset")
    reset
}