package com.example.vaiche_driver.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vaiche_driver.data.repository.OrderRepository
import com.example.vaiche_driver.model.NearbyOrderPublic
import com.example.vaiche_driver.model.OrderPublic
import kotlinx.coroutines.launch

class NewOrderViewModel : ViewModel() {

    private val orderRepository = OrderRepository()

    // Hiển thị lên dialog Nearby → dùng NearbyOrderPublic
    private val _order = MutableLiveData<NearbyOrderPublic?>()
    val order: LiveData<NearbyOrderPublic?> = _order

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<Event<String>>()
    val errorMessage: LiveData<Event<String>> = _errorMessage

    /**
     * Giữ nguyên tên hàm bạn đang gọi: loadOrderDetails(orderId)
     * Ở đây mình lấy /api/orders/{id} (OrderPublic) rồi map “giản lược” sang NearbyOrderPublic
     * (distance/time có thể null nếu chưa tính route).
     */
    fun loadOrderDetails(orderId: String?) {
        if (orderId == null) {
            _errorMessage.value = Event("Order ID is missing.")
            return
        }
        _isLoading.value = true
        viewModelScope.launch {
            val result = orderRepository.getOrderById(orderId)
            result.onSuccess { orderPublic ->
                _order.value = orderPublic.toNearbyShallow()
            }.onFailure { error ->
                _errorMessage.value = Event(error.message ?: "Failed to load order.")
                _order.value = null
            }
            _isLoading.value = false
        }
    }

    /**
     * Giữ nguyên tên hàm: acceptOrder { ... }
     * Gọi API nhận đơn, rồi lấy lại chi tiết đơn để trả về OrderPublic cho SharedViewModel.
     */
    fun acceptOrder(orderId: String, onAccepted: (OrderPublic) -> Unit) {
        viewModelScope.launch {
            val acceptRes = orderRepository.acceptOrder(orderId)
            acceptRes.onSuccess {
                // fetch lại chi tiết
                val detailRes = orderRepository.getOrderById(orderId)
                detailRes.onSuccess { acceptedOrder ->
                    onAccepted(acceptedOrder)
                }.onFailure { e ->
                    _errorMessage.value = Event(e.message ?: "Accepted but failed to fetch order.")
                }
            }.onFailure { e ->
                _errorMessage.value = Event(e.message ?: "Failed to accept order.")
            }
        }
    }

    /**
     * Giữ nguyên tên hàm: rejectOrder { ... }
     */
    fun rejectOrder(orderId: String, onRejected: (String) -> Unit) {
        onRejected(orderId)
    }

    // --- Mapper “giản lược” từ OrderPublic → NearbyOrderPublic (không có route) ---
    private fun OrderPublic.toNearbyShallow(): NearbyOrderPublic {
        return NearbyOrderPublic(
            id = this.id,
            ownerId = this.ownerId,
            collectorId = this.collectorId,
            status = this.status, // BackendOrderStatus
            pickupAddress = this.pickupAddress,
            location = this.location ?: emptyMap(),
            imgUrl1 = this.imgUrl1,
            imgUrl2 = this.imgUrl2,
            items = this.items,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
            distanceKm = 0.0,            // chưa tính (server mới trả)
            travelTimeSeconds = null,     // chưa tính
            travelDistanceMeters = null   // chưa tính
        )
    }
}
