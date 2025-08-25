package com.example.vaicheuserapp.data.model

import com.google.gson.annotations.SerializedName
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CategoryPublic(
    val id: String,
    val name: String,
    val slug: String,
    val description: String?,
    val unit: String?,
    @SerializedName("icon_url")
    val iconUrl: String?,
    @SerializedName("estimated_price_per_unit")
    val price: Double?
) : Parcelable