package com.example.vaicheuserapp.data.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class OrderItemPublic(
    val id: String,
    @SerializedName("order_id") val orderId: String,
    @SerializedName("category_id") val categoryId: String,
    val quantity: Double,
) : Parcelable

@Parcelize
data class GeoJsonPoint(
    val type: String, // "Point"
    val coordinates: List<Double> // [longitude, latitude]
) : Parcelable

@Parcelize
data class OrderPublic(
    val id: String,
    @SerializedName("owner_id") val ownerId: String,
    @SerializedName("collector_id") val collectorId: String?,
    val status: OrderStatus,
    @SerializedName("pickup_address") val pickupAddress: String,
    @SerializedName("location") val locationGeoJson: GeoJsonPoint, // <-- CRITICAL FIX: Rename to locationGeoJson and use GeoJsonPoint
    @SerializedName("img_url1") val imgUrl1: String?,
    @SerializedName("img_url2") val imgUrl2: String?,
    @SerializedName("items") val items: List<OrderItemPublic>,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("owner") val owner: UserPublic?, // Owner is now included
    @SerializedName("collector") val collector: CollectorPublic? // Collector is now included (from getOrder API, not OrderPublic schema itself)
) : Parcelable

enum class OrderStatus {
    @SerializedName("pending") PENDING,
    @SerializedName("accepted") ACCEPTED,
    @SerializedName("completed") COMPLETED,
    @SerializedName("cancelled") CANCELLED
}

data class OrderCreate(
    @SerializedName("pickup_address") val pickupAddress: String,
)