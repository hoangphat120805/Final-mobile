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
data class OrderPublic(
    val id: String,
    @SerializedName("owner_id") val ownerId: String,
    @SerializedName("collector_id") val collectorId: String?,
    val status: OrderStatus,
    @SerializedName("pickup_address") val pickupAddress: String,
    val location: LocationData, // Flexible for Mapbox/GeoJSON
    @SerializedName("img_url1") val imgUrl1: String?, // <-- NEW FIELD
    @SerializedName("img_url2") val imgUrl2: String?, // <-- NEW FIELD
    @SerializedName("created_at") val createdAt: String,
    val items: List<OrderItemPublic> // Default empty list
) : Parcelable

enum class OrderStatus {
    @SerializedName("pending") PENDING,
    @SerializedName("accepted") ACCEPTED,
    @SerializedName("completed") COMPLETED,
    @SerializedName("cancelled") CANCELLED
}