package com.example.vaiche_driver.model

import com.google.gson.annotations.SerializedName

// =======================================================
// ================ AUTH / OTP / RESET ===================
// =======================================================

data class UserLoginRequest(
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("password") val password: String
)

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String
)

data class CollectorRegisterRequest(
    @SerializedName("email") val email: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("password") val password: String,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("register_token") val registerToken: String
)

/** Theo spec: purpose chỉ nhận "register" | "reset" */
// com.example.vaiche_driver.model
enum class OTPPurpose {
    @com.google.gson.annotations.SerializedName("register") REGISTER,
    @com.google.gson.annotations.SerializedName("reset")     RESET;

    companion object {
        fun fromString(raw: String): OTPPurpose = when (raw.lowercase()) {
            "register" -> REGISTER
            "reset"    -> RESET
            else       -> RESET
        }
    }
}


data class OtpRequest(
    @SerializedName("email") val email: String,
    @SerializedName("purpose") val purpose: OTPPurpose
)

data class OtpVerifyRequest(
    @SerializedName("email") val email: String,
    @SerializedName("otp") val otp: String,
    @SerializedName("purpose") val purpose: OTPPurpose
)

data class OtpVerifyResponse(
    @SerializedName("token") val verificationToken: String
)

data class ResetPasswordRequest(
    @SerializedName("email") val email: String,
    @SerializedName("reset_token") val resetToken: String,
    @SerializedName("new_password") val newPassword: String
)

/** Cho PATCH /api/user/me/password */
data class UpdatePassword(
    @SerializedName("old_password") val oldPassword: String,
    @SerializedName("new_password") val newPassword: String
)

// =======================================================
// ===================== USER / COLLECTOR ===============
// =======================================================

/** Enum role theo spec */
enum class UserRole {
    @SerializedName("admin") ADMIN,
    @SerializedName("user") USER,
    @SerializedName("collector") COLLECTOR,
    @SerializedName("business") BUSINESS
}

/** Theo spec: email, full_name, phone_number, id, role là REQUIRED */
data class UserPublic(
    @SerializedName("id") val id: String,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("role") val role: UserRole,
    @SerializedName("email") val email: String,
    @SerializedName("gender") val gender: String?,
    @SerializedName("birth_date") val birthDate: String?,
    @SerializedName("avt_url") val avatarUrl: String?
)

data class UserReadMinimal(
    @SerializedName("id") val id: String,
    @SerializedName("full_name") val fullName: String?,
    @SerializedName("phone_number") val phoneNumber: String
)

/** role là enum UserRole để đồng bộ với backend */
data class CollectorPublic(
    @SerializedName("email") val email: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("gender") val gender: String?,
    @SerializedName("birth_date") val birthDate: String?,
    @SerializedName("avt_url") val avatarUrl: String,
    @SerializedName("id") val id: String,
    @SerializedName("role") val role: UserRole,
    @SerializedName("average_rating") val averageRating: Double?
)

// =======================================================
// ======================= CATEGORY ======================
// =======================================================

data class CategoryPublic(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("slug") val slug: String,
    @SerializedName("description") val description: String?,
    @SerializedName("unit") val unit: String = "kg",
    @SerializedName("icon_url") val iconUrl: String?,
    @SerializedName("estimated_price_per_unit") val estimatedPricePerUnit: Double?,
    @SerializedName("created_by") val createdBy: String,
    @SerializedName("last_updated_by") val lastUpdatedBy: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String
)

// =======================================================
// =================== ENUMS / CONSTANTS =================
// =======================================================

enum class TransactionMethod {
    @SerializedName("cash") CASH,
    @SerializedName("wallet") WALLET
}

enum class TransactionStatus {
    @SerializedName("successful") SUCCESSFUL,
    @SerializedName("failed") FAILED,
    @SerializedName("pending") PENDING
}

/** Bổ sung vì đang được dùng trong OrderPublic/NearbyOrderPublic */
enum class OrderStatusApi {
    @SerializedName("pending") PENDING,
    @SerializedName("accepted") ACCEPTED,
    @SerializedName("completed") COMPLETED,
    @SerializedName("cancelled") CANCELLED
}
// =======================================================
// ========================= ORDERS ======================
// =======================================================

data class OrderItemPublic(
    @SerializedName("id") val id: String,
    @SerializedName("order_id") val orderId: String,
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("quantity") val quantity: Double,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String
)

data class OrderPublic(
    @SerializedName("id") val id: String,
    @SerializedName("owner_id") val ownerId: String,
    @SerializedName("collector_id") val collectorId: String?, // null nếu chưa nhận
    @SerializedName("status") val status: OrderStatusApi,
    @SerializedName("pickup_address") val pickupAddress: String,
    @SerializedName("location") val location: Map<String, @JvmSuppressWildcards Any?>?, // additionalProperties: true
    @SerializedName("img_url1") val imgUrl1: String?,
    @SerializedName("img_url2") val imgUrl2: String?,
    @SerializedName("items") val items: List<OrderItemPublic> = emptyList(),
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    // thêm để hỗ trợ include_user / include_collector
    @SerializedName("owner") val owner: UserPublic? = null,
    @SerializedName("collector") val collector: UserPublic? = null
)

data class NearbyOrderPublic(
    @SerializedName("id") val id: String,
    @SerializedName("owner_id") val ownerId: String,
    @SerializedName("collector_id") val collectorId: String?,
    @SerializedName("status") val status: OrderStatusApi,
    @SerializedName("pickup_address") val pickupAddress: String,
    @SerializedName("location") val location: Map<String, @JvmSuppressWildcards Any?>?,
    @SerializedName("img_url1") val imgUrl1: String?,
    @SerializedName("img_url2") val imgUrl2: String?,
    @SerializedName("items") val items: List<OrderItemPublic> = emptyList(),
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    // thêm để phòng trường hợp backend trả kèm
    @SerializedName("owner") val owner: UserPublic? = null,
    @SerializedName("collector") val collector: UserPublic? = null,
    @SerializedName("distance_km") val distanceKm: Double,
    @SerializedName("travel_time_seconds") val travelTimeSeconds: Double?,
    @SerializedName("travel_distance_meters") val travelDistanceMeters: Double?
)

// ----------------- Requests / Responses ----------------

data class OrderCreate(
    @SerializedName("pickup_address") val pickupAddress: String
)

data class OrderItemCreate(
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("quantity") val quantity: Double
)

data class OrderItemUpdate(
    @SerializedName("quantity") val quantity: Double
)

data class OrderAcceptRequest(
    @SerializedName("note") val note: String? = null
)

data class OrderAcceptResponse(
    @SerializedName("id") val id: String,
    @SerializedName("status") val status: OrderStatusApi,
    @SerializedName("owner_id") val ownerId: String,
    @SerializedName("collector_id") val collectorId: String?
)

data class CompletedOrderItemPayload(
    @SerializedName("order_item_id") val orderItemId: String,
    @SerializedName("actual_quantity") val actualQuantity: Double
)

data class OrderCompletionRequest(
    @SerializedName("payment_method") val paymentMethod: TransactionMethod,
    @SerializedName("items") val items: List<CompletedOrderItemPayload>
)

// =======================================================
// ===================== TRANSACTIONS ====================
// =======================================================

data class TransactionReadResponse(
    @SerializedName("id") val id: String,
    @SerializedName("order_id") val orderId: String,
    @SerializedName("amount") val amount: Double,
    @SerializedName("method") val method: TransactionMethod,
    @SerializedName("status") val status: TransactionStatus,
    @SerializedName("transaction_date") val transactionDate: String,
    @SerializedName("payer") val payer: UserReadMinimal,
    @SerializedName("payee") val payee: UserReadMinimal
)

// =======================================================
// ======================== ROUTE ========================
// =======================================================

data class RoutePublic(
    @SerializedName("distance_meters") val distanceMeters: Double,
    @SerializedName("duration_seconds") val durationSeconds: Double,
    @SerializedName("polyline") val polyline: String
)

// =======================================================
// ======================== REVIEW =======================
// =======================================================

data class ReviewCreate(
    @SerializedName("rating") val rating: Int, // 1..5
    @SerializedName("comment") val comment: String? = null
)

data class ReviewPublic(
    @SerializedName("id") val id: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("order_id") val orderId: String,
    @SerializedName("rating") val rating: Int,
    @SerializedName("comment") val comment: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String
)

// =======================================================
// ==================== NOTIFICATIONS ====================
// =======================================================

data class UserNotification(
    @SerializedName("title") val title: String,
    @SerializedName("message") val message: String,
    @SerializedName("id") val id: String,
    @SerializedName("is_read") val isRead: Boolean,
    @SerializedName("created_at") val createdAt: String
)

// =======================================================
// ========================= MISC ========================
// =======================================================

data class MessageResponse(
    @SerializedName("message") val message: String
)
