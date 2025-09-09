package com.example.vaiche_driver.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ===========================
 * Extensions & Mappers (UI)
 * ===========================
 *
 * Quy ước:
 * - Backend enum: OrderStatusApi { PENDING, ACCEPTED, COMPLETED, CANCELLED }
 * - Local UI enum: OrderStatus { scheduled, delivering, completed, cancelled, pending }
 *
 * Rule duy nhất map backend -> local:
 * - PENDING   -> pending
 * - COMPLETED -> completed
 * - CANCELLED -> cancelled
 * - ACCEPTED  -> if (imgUrl1 is null/blank) scheduled else delivering
 */

/** Map backend enum + context ảnh -> local UI enum */
fun mapBackendToLocalStatus(status: OrderStatusApi, imgUrl1: String?): OrderStatus = when (status) {
    OrderStatusApi.PENDING   -> OrderStatus.pending
    OrderStatusApi.COMPLETED -> OrderStatus.completed
    OrderStatusApi.CANCELLED -> OrderStatus.cancelled
    OrderStatusApi.ACCEPTED  -> if (imgUrl1.isNullOrBlank()) OrderStatus.scheduled else OrderStatus.delivering
}

/** Convenience: lấy local status trực tiếp từ OrderPublic */
fun OrderPublic.localStatus(): OrderStatus =
    mapBackendToLocalStatus(this.status, this.imgUrl1)

/** Convenience: lấy local status trực tiếp từ NearbyOrderPublic */
fun NearbyOrderPublic.localStatus(): OrderStatus =
    mapBackendToLocalStatus(this.status, this.imgUrl1)

/** Parse ISO-8601 timestamp -> Pair(time, date) với nhiều pattern fallback */
private fun String.toPrettyTimeDate(): Pair<String, String> {
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSX",
        "yyyy-MM-dd'T'HH:mm:ssX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'"
    )
    val parsed: Date? = patterns.firstNotNullOfOrNull { p ->
        try { SimpleDateFormat(p, Locale.getDefault()).parse(this) } catch (_: Exception) { null }
    }
    return if (parsed != null) {
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(parsed)
        val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(parsed)
        time to date
    } else {
        "" to ""
    }
}

/** OrderItemPublic (API) -> OrderItem (UI) */
fun OrderItemPublic.toOrderItem(): OrderItem = OrderItem(
    id = id,
    categoryName = categoryId,   // TODO: khi backend trả tên category thì map lại tại đây
    categoryUnit = "unit",       // TODO: map theo unit thật nếu có
    quantity = quantity,
    pricePerUnit = 0.0           // backend chưa trả đơn giá
)

/** OrderPublic (API) -> Schedule (UI) */
fun OrderPublic.toSchedule(statusOverride: OrderStatus? = null): Schedule {
    val (time, date) = createdAt.toPrettyTimeDate()
    val uiStatus = statusOverride ?: localStatus()
    return Schedule(
        id = id,
        date = date,
        time = time,
        status = uiStatus,
        startLocationName = pickupAddress,
        startLocationAddress = pickupAddress,
        endLocationName = "VAI CHE",
        endLocationAddress = "Hồ Chí Minh"
    )
}

/** OrderPublic (API) -> OrderDetail (UI) */
fun OrderPublic.toOrderDetail(statusOverride: OrderStatus? = null): OrderDetail {
    val uiStatus = statusOverride ?: localStatus()
    val uiItems = items.map { it.toOrderItem() }

    val (time, date) = createdAt.toPrettyTimeDate()
    val ts = listOfNotNull(
        time.takeIf { it.isNotBlank() },
        date.takeIf { it.isNotBlank() }
    ).joinToString(", ").ifBlank { "Unknown Time" }

    return OrderDetail(
        id = id,
        user = OrderUser(
            fullName = owner?.fullName ?: "Unknown User",
            phoneNumber = owner?.phoneNumber ?: "N/A",
            avatarUrl = owner?.avatarUrl
        ),
        status = uiStatus,
        pickupTimestamp = ts,
        startLocationName = pickupAddress,
        startLocationAddress = pickupAddress,
        endLocationName = "VAI CHE",
        endLocationAddress = "Hồ Chí Minh",
        items = uiItems,
        totalAmount = 0.0,
        totalWeight = uiItems.filter { it.categoryUnit.equals("kg", true) }.sumOf { it.quantity },
        pickupPhotoUrl = imgUrl1,
        dropoffPhotoUrl = imgUrl2
    )
}
