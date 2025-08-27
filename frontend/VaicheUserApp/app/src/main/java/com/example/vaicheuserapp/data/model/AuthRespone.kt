package com.example.vaicheuserapp.data.model

import com.google.gson.annotations.SerializedName
import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

data class LoginResponse(
    @SerializedName("access_token")
    val accessToken: String,
    @SerializedName("token_type")
    val tokenType: String
)

@Parcelize
data class UserPublic(
    val id: String,
    @SerializedName("full_name")
    val fullName: String?, // Can be null as per API
    @SerializedName("phone_number")
    val phoneNumber: String,
    val role: String,
    val gender: String?,
    @SerializedName("birth_date")
    val birthDate: String?, // Representing date as String "YYYY-MM-DD"
    val email: String?,
    @SerializedName("avt_url")
    val avtUrl: String
) : Parcelable