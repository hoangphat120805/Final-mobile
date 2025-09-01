package com.example.vaicheuserapp.data.model

import com.google.gson.annotations.SerializedName

// For sending OTP to email
data class EmailRequest(
    val email: String
)

// For verifying OTP
data class VerifyOtpRequest(
    val email: String,
    val otp: String
)