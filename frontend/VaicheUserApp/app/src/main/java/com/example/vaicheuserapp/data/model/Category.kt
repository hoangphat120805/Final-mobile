package com.example.vaicheuserapp.data.model

import com.google.gson.annotations.SerializedName

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
)