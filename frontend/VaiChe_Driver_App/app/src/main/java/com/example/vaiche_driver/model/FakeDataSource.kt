package com.example.vaiche_driver.model

import com.example.vaiche_driver.fragment.OrderDetailFragment
import java.util.UUID

/**
 * Lớp này hoạt động như một nguồn dữ liệu trung tâm, giả lập một database hoặc API.
 * SAU NÀY, BẠN SẼ THAY THẾ LOGIC TRONG CÁC HÀM NÀY BẰNG CÁC LỆNH GỌI API THỰC TẾ.
 */
object FakeDataSource {

    // Danh sách này phải là MutableList để có thể cập nhật trạng thái/ảnh
    private var allOrderDetails: MutableList<OrderDetail>

    init {
        val user = OrderUser("User", "070089***", "https://i.pravatar.cc/150?img=5")

        val itemsSet1 = listOf(
            OrderItem(UUID.randomUUID().toString(), "Steel", "kg", 6.52, 5000.0),
            OrderItem(UUID.randomUUID().toString(), "Paper", "kg", 0.35, 4000.0),
            OrderItem(UUID.randomUUID().toString(), "Can", "can", 3.0, 250.0)
        )
        val itemsSet2 = listOf(
            OrderItem(UUID.randomUUID().toString(), "Plastic Bottle", "kg", 5.1, 3500.0),
            OrderItem(UUID.randomUUID().toString(), "Glass Bottle", "kg", 12.0, 1500.0)
        )

        // Khởi tạo danh sách dữ liệu mẫu với các trạng thái khác nhau
        allOrderDetails = mutableListOf(
            createOrderDetail("order_pending_1", OrderStatus.pending, user, itemsSet1), // Đơn hàng đang chờ 1
            createOrderDetail("order_pending_2", OrderStatus.pending, user, itemsSet2), // Đơn hàng đang chờ 2
            // Không có đơn hàng accepted/delivering ban đầu,
            // chúng sẽ được tạo ra khi người dùng chấp nhận đơn pending.
            createOrderDetail("order_completed_1", OrderStatus.completed, user, itemsSet2),
            createOrderDetail("order_completed_2", OrderStatus.completed, user, itemsSet1),
            createOrderDetail("order_completed_3", OrderStatus.completed, user, itemsSet1),
            createOrderDetail("order_completed_4", OrderStatus.completed, user, itemsSet1)
        )
    }

    /**
     * Lấy danh sách tóm tắt (Schedule) cho màn hình danh sách.
     */
    fun getSchedules(): List<Schedule> {
        return allOrderDetails.map { detail ->
            val parts = detail.pickupTimestamp.split(',', limit = 2)
            val time = parts.getOrNull(0) ?: ""
            val date = parts.getOrNull(1)?.trim() ?: ""

            Schedule(
                id = detail.id,
                date = date,
                time = time,
                status = detail.status,
                startLocationName = detail.startLocationName,
                startLocationAddress = detail.startLocationAddress,
                endLocationName = detail.endLocationName,
                endLocationAddress = detail.endLocationAddress
            )
        }
    }

    /**
     * Lấy chi tiết của MỘT đơn hàng dựa trên ID.
     */
    fun getOrderDetailById(id: String?): OrderDetail? {
        if (id == null) return null
        return allOrderDetails.find { it.id == id }
    }

    /**
     * Lấy một đơn hàng đang ở trạng thái chờ (pending).
     * Hàm này sẽ bỏ qua bất kỳ đơn hàng nào có ID nằm trong danh sách đã từ chối.
     * @param rejectedIds Một Set chứa các ID của đơn hàng đã bị từ chối.
     * @return Một OrderDetail hoặc null nếu không tìm thấy.
     */
    fun getPendingOrder(rejectedIds: Set<String>): OrderDetail? {
        // Tìm đơn hàng đầu tiên có trạng thái là "pending"
        // VÀ id của nó không nằm trong danh sách `rejectedIds`
        return allOrderDetails.find { it.status == OrderStatus.pending && it.id !in rejectedIds }
    }

    /**
     * Cập nhật trạng thái của một đơn hàng.
     */
    fun updateOrderStatus(orderId: String?, newStatus: OrderStatus) {
        val index = allOrderDetails.indexOfFirst { it.id == orderId }
        if (index != -1) {
            val updatedOrder = allOrderDetails[index].copy(status = newStatus)
            allOrderDetails[index] = updatedOrder
        }
    }

    /**
     * Cập nhật URL ảnh cho một đơn hàng.
     */
    fun updatePhotoUrl(orderId: String?, url: String, target: OrderDetailFragment.PhotoTarget) {
        val index = allOrderDetails.indexOfFirst { it.id == orderId }
        if (index != -1) {
            val currentOrder = allOrderDetails[index]
            val updatedOrder = when (target) {
                OrderDetailFragment.PhotoTarget.PICKUP -> currentOrder.copy(pickupPhotoUrl = url)
                OrderDetailFragment.PhotoTarget.DROPOFF -> currentOrder.copy(dropoffPhotoUrl = url)
            }
            allOrderDetails[index] = updatedOrder
        }
    }

    /**
     * Hàm tiện ích để tạo một đối tượng OrderDetail hoàn chỉnh.
     */
    private fun createOrderDetail(id: String, status: OrderStatus, user: OrderUser, items: List<OrderItem>): OrderDetail {
        val totalAmount = items.sumOf { it.quantity * it.pricePerUnit }
        val totalWeight = items.filter { it.categoryUnit == "kg" }.sumOf { it.quantity }
        val pickupPhoto = if (status == OrderStatus.delivering || status == OrderStatus.completed) "https://picsum.photos/seed/$id-pickup/400/300" else null
        val dropoffPhoto = if (status == OrderStatus.completed) "https://picsum.photos/seed/$id-dropoff/400/300" else null

        return OrderDetail(
            id = id,
            user = user,
            status = status,
            pickupTimestamp = "9:45, ${id.takeLast(1)}/07/2025",
            startLocationName = "Nguyễn Văn Cừ",
            startLocationAddress = "Quận 5, Hồ Chí Minh",
            endLocationName = "VAI CHE",
            endLocationAddress = "Hồ Chí Minh",
            noteFromUser = "My house is next to the department store",
            items = items,
            totalAmount = totalAmount,
            totalWeight = totalWeight,
            pickupPhotoUrl = pickupPhoto,
            dropoffPhotoUrl = dropoffPhoto
        )
    }
}