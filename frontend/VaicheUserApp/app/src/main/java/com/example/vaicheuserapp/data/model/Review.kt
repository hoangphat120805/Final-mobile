package com.example.vaicheuserapp.data.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class ReviewCreate(
    val rating: Int,
    val comment: String? // Optional
) : Parcelable

@Parcelize
data class ReviewPublic(
    val id: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("order_id") val orderId: String,
    val rating: Int,
    val comment: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String
) : Parcelable