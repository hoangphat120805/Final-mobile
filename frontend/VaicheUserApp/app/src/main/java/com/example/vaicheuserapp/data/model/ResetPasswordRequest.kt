package com.example.vaicheuserapp.data.model

import com.google.gson.annotations.SerializedName

// For the final password reset
data class ResetPasswordPayload( // Renamed to avoid clash with API's UpdatePassword
    val email: String,
    @SerializedName("reset_token") // This is the OTP verified earlier
    val resetToken: String,
    @SerializedName("new_password")
    val newPassword: String
)