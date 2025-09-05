package com.example.vaicheuserapp.data.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class CollectorPublic(
    val id: String,
    val email: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("full_name") val fullName: String,
    val gender: String?,
    @SerializedName("birth_date") val birthDate: String?,
    @SerializedName("avt_url") val avtUrl: String?,
    val role: String,
    @SerializedName("average_rating") val averageRating: Double?
) : Parcelable