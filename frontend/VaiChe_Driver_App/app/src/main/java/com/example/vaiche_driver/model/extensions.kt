package com.example.vaiche_driver.model

//import com.mapbox.api.directions.v5.DirectionsCriteria
//import com.mapbox.api.directions.v5.models.DirectionsRoute
//import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.geojson.Point
import com.mapbox.geojson.utils.PolylineUtils
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

//fun RoutePublic.toDirectionsRoute(precision: Int): DirectionsRoute {
//    val coords = PolylineUtils.decode(polyline, precision)
//    val opts = RouteOptions.builder()
//        .profile(DirectionsCriteria.PROFILE_DRIVING)
//        .coordinatesList(coords)
//        .build()
//    return DirectionsRoute.builder()
//        .geometry(polyline)
//        .distance(distanceMeters)
//        .duration(durationSeconds)
//        .routeOptions(opts)
//        .build()
//}

fun OrderPublic.toOriginDestination(): Pair<Point, Point>? {
    val loc = location ?: return null

    fun num(key: String) = (loc[key] as? Number)?.toDouble()

    // ĐẶT các khóa theo backend của bạn (ví dụ này):
    val pLat = num("pickup_lat") ?: num("start_lat")
    val pLng = num("pickup_lng") ?: num("start_lng")
    val dLat = num("drop_lat")   ?: num("end_lat")
    val dLng = num("drop_lng")   ?: num("end_lng")

    if (pLat == null || pLng == null || dLat == null || dLng == null) return null
    return Point.fromLngLat(pLng, pLat) to Point.fromLngLat(dLng, dLat)
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
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",   // 6 chữ số microseconds
        "yyyy-MM-dd'T'HH:mm:ss.SSS",      // 3 chữ số millis
        "yyyy-MM-dd'T'HH:mm:ss"           // không có phần thập phân
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


/**
 * OrderItemPublic (API) -> OrderItem (UI)
 * Dùng map categoriesById để enrich thông tin category.
 */
fun OrderItemPublic.toOrderItem(categoriesById: Map<String, CategoryPublic>): OrderItem {
    val cat = categoriesById[this.categoryId]
    return OrderItem(
        id = id,
        categoryId = categoryId,
        categoryName = cat?.name ?: categoryId, // fallback: dùng id nếu không tìm thấy
        categoryUnit = cat?.unit ?: "kg",
        quantity = quantity,
        pricePerUnit = cat?.estimatedPricePerUnit ?: 0.0
    )
}

/**
 * Fallback: khi không có categories
 */
fun OrderItemPublic.toOrderItemFallback(): OrderItem = OrderItem(
    id = id,
    categoryId = categoryId,
    categoryName = categoryId,
    categoryUnit = "kg",
    quantity = quantity,
    pricePerUnit = 0.0
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
        startLocationName = owner?.fullName ?: "Unknown User",
        startLocationAddress = pickupAddress,
        endLocationName = "VAI CHE",
        endLocationAddress = "Hồ Chí Minh"
    )
}

/** OrderPublic (API) -> OrderDetail (UI) — với categories enrich */
fun OrderPublic.toOrderDetail(
    categories: List<CategoryPublic>,
    statusOverride: OrderStatus? = null
): OrderDetail {
    val categoriesById = categories.associateBy { it.id }
    val uiStatus = statusOverride ?: localStatus()
    val uiItems = items.map { it.toOrderItem(categoriesById) }

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
        startLocationName = owner?.fullName ?: "Unknown User",
        startLocationAddress = pickupAddress,
        endLocationName = "VAI CHE",
        endLocationAddress = "Hồ Chí Minh",
        items = uiItems,
        totalAmount = uiItems.sumOf { it.quantity * it.pricePerUnit },
        totalWeight = uiItems.filter { it.categoryUnit.equals("kg", true) }.sumOf { it.quantity },
        pickupPhotoUrl = imgUrl1,
        dropoffPhotoUrl = imgUrl2
    )
}

/**
 * OrderPublic (API) -> OrderDetail (UI) — fallback nếu không gọi categories
 */
fun OrderPublic.toOrderDetailFallback(
    statusOverride: OrderStatus? = null
): OrderDetail {
    val uiStatus = statusOverride ?: localStatus()
    val uiItems = items.map { it.toOrderItemFallback() }

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
