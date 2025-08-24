package com.example.vaicheuserapp.data.model

import com.google.gson.annotations.SerializedName

data class LoginResponse(
    @SerializedName("access_token")
    val accessToken: String,
    @SerializedName("token_type")
    val tokenType: String
)

data class UserPublic(
    val id: String,
    @SerializedName("full_name")
    val fullName: String?,
    @SerializedName("phone_number")
    val phoneNumber: String,
    val role: String
)