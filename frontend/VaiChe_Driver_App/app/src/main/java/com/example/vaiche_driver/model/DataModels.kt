package com.example.vaiche_driver.model

import com.google.gson.annotations.SerializedName

/**
 * Các file này chứa các data class đại diện cho các đối tượng JSON
 * được trả về từ API. Annotation @SerializedName được dùng để ánh xạ
 * key trong JSON (ví dụ: "full_name") với tên biến trong Kotlin (ví dụ: fullName).
 */

//==================== USER MODELS ====================

data class UserPublic(
    @SerializedName("id") val id: String,
    @SerializedName("full_name") val fullName: String?,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("avt_url") val avatarUrl: String?,
    @SerializedName("role") val role: String
)

// Dùng để gửi khi đăng ký tài khoản collector/driver
data class UserCreate(
    @SerializedName("email") val email: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("password") val password: String,
    @SerializedName("role") val role: String = "collector" // Mặc định là collector
)

//==================== CATEGORY MODELS ====================

data class CategoryPublic(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("slug") val slug: String,
    @SerializedName("unit") val unit: String,
    @SerializedName("estimated_price_per_unit") val pricePerUnit: Double
)

//==================== ORDER MODELS ====================

// Model cho một item bên trong đơn hàng
data class OrderItemPublic(
    @SerializedName("id") val id: String,
    @SerializedName("order_id") val orderId: String,
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("quantity") val quantity: Double,
    @SerializedName("price_per_unit") val pricePerUnit: Double,

    // Các trường này cần được backend "làm giàu" (enrich) dữ liệu để UI hiển thị dễ hơn
    var categoryName: String? = null,
    var categoryUnit: String? = null
)

// Model cho một đơn hàng chi tiết
data class OrderPublic(
    @SerializedName("id") val id: String,
    @SerializedName("owner_id") val ownerId: String,
    @SerializedName("collector_id") val collectorId: String?,
    @SerializedName("status") val status: String, // "pending", "accepted", "completed", "cancelled"
    @SerializedName("pickup_address") val pickupAddress: String,
    @SerializedName("location") val location: LocationObject?, // Sẽ cần định nghĩa LocationObject
    @SerializedName("items") val items: List<OrderItemPublic> = emptyList(),

    // Các trường này cần được backend bổ sung vào schema `OrderPublic`
    var note: String? = "My house is next to the department store", // Dữ liệu giả
    var pickupTimestamp: String? = "9:45, 08/07/2025", // Dữ liệu giả
    var owner: UserPublic? = null // Backend nên trả về thông tin user của đơn hàng
)

// Dùng trong OrderPublic
data class LocationObject(
    // Định nghĩa các trường bên trong `location` nếu cần, ví dụ:
    val type: String?,
    val coordinates: List<Double>?
)

// Dùng để tạo một đơn hàng mới (từ user app)
data class OrderCreate(
    @SerializedName("pickup_address") val pickupAddress: String
)

// Dùng để thêm item vào một đơn hàng
data class OrderItemCreate(
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("quantity") val quantity: Double,
    @SerializedName("price_per_unit") val pricePerUnit: Double
)

// Dùng trong request body khi hoàn thành đơn hàng
data class CompletedOrderItemPayload(
    @SerializedName("order_item_id") val orderItemId: String,
    @SerializedName("actual_quantity") val actualQuantity: Double
)

// Request body chính khi hoàn thành đơn hàng
data class OrderCompletionRequest(
    @SerializedName("payment_method") val paymentMethod: String = "cash",
    @SerializedName("items") val items: List<CompletedOrderItemPayload>
)