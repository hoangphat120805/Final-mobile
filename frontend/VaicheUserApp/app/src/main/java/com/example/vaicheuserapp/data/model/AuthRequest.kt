package com.example.vaicheuserapp.data.model

import com.google.gson.annotations.SerializedName

data class UserCreateRequest(
    @SerializedName("phone_number")
    val phoneNumber: String,
    val password: String,
    @SerializedName("full_name") // <-- ADDED
    val fullName: String,       // <-- ADDED
    val email: String           // <-- ADDED
)