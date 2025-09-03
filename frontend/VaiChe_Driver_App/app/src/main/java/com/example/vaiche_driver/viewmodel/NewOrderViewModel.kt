package com.example.vaiche_driver.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vaiche_driver.data.repository.OrderRepository
import com.example.vaiche_driver.model.OrderDetail
import com.example.vaiche_driver.model.Schedule
import com.example.vaiche_driver.model.OrderStatus
import kotlinx.coroutines.launch

class NewOrderViewModel : ViewModel() {

    private val orderRepository = OrderRepository()

    // LiveData này giữ OrderDetail (UI Model)
    private val _order = MutableLiveData<OrderDetail?>()
    val order: LiveData<OrderDetail?> = _order

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<Event<String>>()
    val errorMessage: LiveData<Event<String>> = _errorMessage

    /**
     * Tải chi tiết của một đơn hàng. Repository sẽ trả về đúng kiểu OrderDetail.
     */
    fun loadOrderDetails(orderId: String?) {
        if (orderId == null) {
            _errorMessage.value = Event("Order ID is missing.")
            return
        }
        _isLoading.value = true
        viewModelScope.launch {
            val result = orderRepository.getOrderDetail(orderId)
            result.onSuccess { orderDetail ->
                // Gán trực tiếp vì Repository đã trả về đúng kiểu
                _order.value = orderDetail
            }.onFailure { error ->
                _errorMessage.value = Event(error.message ?: "Failed to load order details.")
            }
            _isLoading.value = false
        }
    }

    /**
     * Xử lý khi người dùng nhấn "Accept".
     * Cung cấp một đối tượng Schedule cho callback.
     */
    fun acceptOrder(onAccepted: (Schedule) -> Unit) {
        val orderToAccept = _order.value ?: return
        onAccepted(orderToAccept.toSchedule())
    }

    /**
     * Xử lý khi người dùng nhấn "Reject".
     */
    fun rejectOrder(onRejected: (String) -> Unit) {
        val orderToReject = _order.value ?: return
        onRejected(orderToReject.id)
    }

    /**
     * Hàm tiện ích để chuyển đổi một đối tượng OrderDetail (UI model) thành Schedule.
     */
    private fun OrderDetail.toSchedule(): Schedule {
        return Schedule(
            id = this.id,
            date = this.pickupTimestamp.split(',', limit = 2).getOrNull(1)?.trim() ?: "",
            time = this.pickupTimestamp.split(',', limit = 2).getOrNull(0)?.trim() ?: "",
            status = OrderStatus.scheduled, // Gán trạng thái mới
            startLocationName = this.startLocationName,
            startLocationAddress = this.startLocationAddress,
            endLocationName = this.endLocationName,
            endLocationAddress = this.endLocationAddress
        )
    }
}