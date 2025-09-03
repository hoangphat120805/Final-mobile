package com.example.vaiche_driver.model

/**
 * File này chứa các hàm mở rộng (extension functions) để chuyển đổi
 * giữa các model mạng (Network models - ...Public) và các model giao diện (UI models).
 * Điều này giúp giữ cho logic chuyển đổi được tập trung ở một nơi.
 */

/**
 * Chuyển đổi một đối tượng OrderPublic (từ API) thành một đối tượng Schedule (cho UI).
 */
fun OrderPublic.toSchedule(): Schedule {
    // Tách chuỗi timestamp thành giờ và ngày
    val timestamp = this.pickupTimestamp ?: " , "
    val parts = timestamp.split(',', limit = 2)
    val time = parts.getOrNull(0)?.trim() ?: ""
    val date = parts.getOrNull(1)?.trim() ?: ""

    // Chuyển đổi trạng thái từ String sang Enum
    val statusEnum = OrderStatus.fromString(this.status)

    return Schedule(
        id = this.id,
        date = date,
        time = time,
        status = statusEnum,
        startLocationName = this.pickupAddress, // Tạm thời dùng pickupAddress làm tên
        startLocationAddress = this.pickupAddress,
        endLocationName = "VAI CHE", // Dữ liệu giả, cần được backend cung cấp
        endLocationAddress = "Hồ Chí Minh" // Dữ liệu giả
    )
}

/**
 * --- HÀM QUAN TRỌNG ---
 * Chuyển đổi một đối tượng OrderPublic (từ API) thành một đối tượng OrderDetail (cho UI).
 */
fun OrderPublic.toOrderDetail(): OrderDetail {
    val statusEnum = OrderStatus.fromString(this.status)

    // Chuyển đổi danh sách OrderItemPublic sang OrderItem
    val uiItems = this.items.map { it.toOrderItem() }

    // Tạo đối tượng User cho UI từ dữ liệu owner
    // Nếu backend không trả về owner, tạo một user mặc định
    val uiUser = this.owner?.let {
        OrderUser(it.fullName, it.phoneNumber, it.avatarUrl)
    } ?: OrderUser("Unknown User", "N/A", null)

    return OrderDetail(
        id = this.id,
        user = uiUser,
        status = statusEnum,
        pickupTimestamp = this.pickupTimestamp ?: "Unknown Time",
        startLocationName = this.pickupAddress,
        startLocationAddress = this.pickupAddress,
        endLocationName = "VAI CHE", // Dữ liệu giả
        endLocationAddress = "Hồ Chí Minh", // Dữ liệu giả
        noteFromUser = this.note ?: "No note from user.",
        items = uiItems,
        // Tính toán lại tổng tiền và cân nặng ở client để đảm bảo chính xác
        totalAmount = uiItems.sumOf { it.quantity * it.pricePerUnit },
        totalWeight = uiItems.filter { it.categoryUnit == "kg" }.sumOf { it.quantity },
        pickupPhotoUrl = null, // Backend cần cung cấp trường này
        dropoffPhotoUrl = null // Backend cần cung cấp trường này
    )
}

/**
 * Chuyển đổi một đối tượng OrderItemPublic (từ API) thành OrderItem (cho UI).
 */
fun OrderItemPublic.toOrderItem(): OrderItem {
    return OrderItem(
        id = this.id,
        // Backend nên trả về categoryName và categoryUnit để không phải gọi API khác
        categoryName = this.categoryName ?: "Unknown Category",
        categoryUnit = this.categoryUnit ?: "unit",
        quantity = this.quantity,
        pricePerUnit = this.pricePerUnit
    )
}

/**
 * Hàm tiện ích để chuyển đổi một chuỗi String thành enum OrderStatus một cách an toàn.
 */
fun OrderStatus.Companion.fromString(value: String): OrderStatus {
    return try {
        OrderStatus.valueOf(value.lowercase())
    } catch (e: IllegalArgumentException) {
        OrderStatus.pending // Trả về một giá trị mặc định nếu chuỗi không hợp lệ
    }
}