package com.example.vaiche_driver.model

import java.util.UUID

/**
 * ENUM DUY NHẤT cho trạng thái đơn hàng, được dùng chung trong toàn bộ ứng dụng.
 */
enum class OrderStatus {
    scheduled,
    delivering,
    completed,
    cancelled,
    pending;

    companion object {
        fun fromString(value: String): OrderStatus {
            return try {
                valueOf(value.lowercase())
            } catch (e: Exception) {
                pending
            }
        }
    }
}

data class OrderUser(
    val fullName: String?,
    val phoneNumber: String,
    val avatarUrl: String?
)

data class OrderItem(
    val id: String,
    val categoryId: String,
    val categoryName: String,
    val categoryUnit: String,
    val quantity: Double,
    val pricePerUnit: Double
)

data class OrderDetail(
    val id: String,
    val user: OrderUser,
    val status: OrderStatus,
    val pickupTimestamp: String,
    val startLocationName: String,
    val startLocationAddress: String,
    val endLocationName: String,
    val endLocationAddress: String,
    val items: List<OrderItem>,
    val totalAmount: Double,
    val totalWeight: Double,
    val pickupPhotoUrl: String?,
    val dropoffPhotoUrl: String?
)

/**
 * --- ĐÂY LÀ SCHEDULE MODEL BẠN CẦN ---
 * Data class NHẸ cho danh sách.
 * Nó chứa TẤT CẢ các thông tin cần thiết để hiển thị một card trong danh sách Schedule.
 */
data class Schedule(
    val id: String,
    val date: String,
    val time: String,
    val status: OrderStatus,
    val startLocationName: String,
    val startLocationAddress: String,
    val endLocationName: String,
    val endLocationAddress: String
)

/**
 * Sealed class để RecyclerView có thể hiển thị nhiều loại item khác nhau.
 * Nó có thể là một Header (tiêu đề) hoặc một ScheduleItem (card lịch trình).
 */
sealed class ScheduleListItem {
    data class Header(val title: String) : ScheduleListItem()
    data class ScheduleItem(val schedule: Schedule) : ScheduleListItem()
}