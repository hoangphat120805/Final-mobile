package com.example.vaiche_driver.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.vaiche_driver.model.FakeDataSource
import com.example.vaiche_driver.model.OrderDetail
import com.example.vaiche_driver.model.OrderStatus
import com.example.vaiche_driver.model.Schedule

class NewOrderViewModel : ViewModel() {

    // LiveData để giữ chi tiết của đơn hàng đang được hiển thị trong dialog
    private val _order = MutableLiveData<OrderDetail?>()
    val order: LiveData<OrderDetail?> = _order

    /**
     * Tải chi tiết của một đơn hàng cụ thể.
     * TƯƠNG LAI: Sẽ gọi API `GET /api/orders/{orderId}`.
     * @param orderId ID của đơn hàng cần hiển thị.
     */
    fun loadOrderDetails(orderId: String?) {
        _order.value = FakeDataSource.getOrderDetailById(orderId)
    }

    /**
     * Xử lý khi người dùng nhấn "Accept".
     * TƯƠNG LAI: Sẽ gọi API `POST /api/orders/{orderId}/accept`.
     * @param onAccepted Callback được gọi khi chấp nhận thành công, dùng để thông báo cho SharedViewModel.
     */
    fun acceptOrder(onAccepted: (Schedule) -> Unit) {
        val orderToAccept = _order.value ?: return

        // Cập nhật trạng thái trong DataSource
        FakeDataSource.updateOrderStatus(orderToAccept.id, OrderStatus.scheduled)

        // Gọi callback để thông báo cho "trung tâm điều khiển" (SharedViewModel)
        // rằng một đơn hàng mới đã được chấp nhận.
        onAccepted(orderToAccept.toSchedule())
    }

    /**
     * Xử lý khi người dùng nhấn "Reject".
     * TƯƠNG LAI: Có thể không cần gọi API, chỉ cần lưu lại ID để bỏ qua.
     * @param onRejected Callback được gọi khi từ chối thành công,
     *                   truyền về ID của đơn hàng đã bị từ chối.
     */
    fun rejectOrder(onRejected: (String) -> Unit) {
        val orderToReject = _order.value ?: return
        onRejected(orderToReject.id)
    }

    /**
     * Hàm tiện ích để chuyển đổi một đối tượng OrderDetail (nặng) thành một đối tượng Schedule (nhẹ).
     * `private` vì chỉ ViewModel này cần dùng nó trong hàm `acceptOrder`.
     */
    private fun OrderDetail.toSchedule(): Schedule {
        val parts = this.pickupTimestamp.split(',', limit = 2)
        val time = parts.getOrNull(0) ?: ""
        val date = parts.getOrNull(1)?.trim() ?: ""

        return Schedule(
            id = this.id,
            date = date,
            time = time,
            status = OrderStatus.scheduled, // Khi chấp nhận, trạng thái mới sẽ là accepted
            startLocationName = this.startLocationName,
            startLocationAddress = this.startLocationAddress,
            endLocationName = this.endLocationName,
            endLocationAddress = this.endLocationAddress
        )
    }
}