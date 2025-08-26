package com.example.vaicheuserapp.data.model

import com.google.gson.annotations.SerializedName

data class UserUpdateRequest(
    @SerializedName("full_name")
    val fullName: String?,
    @SerializedName("phone_number")
    val phoneNumber: String?,
    val email: String?,
    val gender: String?,
    @SerializedName("birth_date")
    val birthDate: String?, // "YYYY-MM-DD" format
    @SerializedName("avt_url") // <-- ADDED: Avatar URL
    val avtUrl: String?
)

data class UserUpdatePasswordRequest(
    @SerializedName("old_password")
    val oldPassword: String,
    @SerializedName("new_password")
    val newPassword: String
)