package com.example.vaiche_driver.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vaiche_driver.data.network.RetrofitClient
import com.example.vaiche_driver.data.repository.OrderRepository
import com.example.vaiche_driver.model.NearbyOrderPublic
import com.example.vaiche_driver.model.OrderPublic
import kotlinx.coroutines.launch

class NewOrderViewModel : ViewModel() {

    private val orderRepository = OrderRepository { RetrofitClient.instance }

    // Dùng NearbyOrderPublic để hiển thị trên dialog
    private val _order = MutableLiveData<NearbyOrderPublic?>()
    val order: LiveData<NearbyOrderPublic?> = _order

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<Event<String>>()
    val errorMessage: LiveData<Event<String>> = _errorMessage

    /**
     * Prime dữ liệu từ kết quả /nearby (đã có distance/time) để hiển thị ngay.
     */
    fun primeWithSeed(seed: NearbyOrderPublic?) {
        if (seed != null) _order.value = seed
    }

    /**
     * Giữ tên cũ bạn đang gọi: loadOrderDetails(orderId)
     * Lấy /api/orders/{id} -> OrderPublic rồi map sang NearbyOrderPublic.
     * Nếu trước đó đã có seed (distance/time) thì giữ lại distance/time.
     */
    fun loadOrderDetails(orderId: String?) {
        if (orderId == null) {
            _errorMessage.value = Event("Order ID is missing.")
            return
        }
        _isLoading.value = true
        viewModelScope.launch {
            val result = orderRepository.getOrderById(orderId)
            result
                .onSuccess { orderPublic ->
                    val shallow = orderPublic.toNearbyShallow()
                    // preserve distance/time nếu có seed cho đúng id
                    val seed = _order.value?.takeIf { it.id == orderPublic.id }
                    _order.value = shallow.copy(
                        distanceKm = seed?.distanceKm ?: shallow.distanceKm,
                        travelTimeSeconds = seed?.travelTimeSeconds ?: shallow.travelTimeSeconds,
                        travelDistanceMeters = seed?.travelDistanceMeters ?: shallow.travelDistanceMeters
                    )
                }
                .onFailure { error ->
                    _errorMessage.value = Event(error.message ?: "Failed to load order.")
                    // vẫn giữ seed nếu có, tránh màn hình trống
                }
            _isLoading.value = false
        }
    }

    /**
     * Accept: gọi API nhận đơn, fetch lại chi tiết và trả về OrderPublic cho caller.
     */
    fun acceptOrder(orderId: String, onAccepted: (OrderPublic) -> Unit) {
        viewModelScope.launch {
            val acceptRes = orderRepository.acceptOrder(orderId)
            acceptRes.onSuccess {
                val detailRes = orderRepository.getOrderById(orderId)
                detailRes
                    .onSuccess { acceptedOrder -> onAccepted(acceptedOrder) }
                    .onFailure { e ->
                        _errorMessage.value =
                            Event(e.message ?: "Accepted but failed to fetch order.")
                    }
            }.onFailure { e ->
                _errorMessage.value = Event(e.message ?: "Failed to accept order.")
            }
        }
    }

    /**
     * Reject: báo ngược lại ID để SharedViewModel lưu vào rejected list.
     */
    fun rejectOrder(orderId: String, onRejected: (String) -> Unit) {
        onRejected(orderId)
    }

    // --- Mapper: OrderPublic -> NearbyOrderPublic (không có route) ---
    private fun OrderPublic.toNearbyShallow(): NearbyOrderPublic {
        return NearbyOrderPublic(
            id = this.id,
            ownerId = this.ownerId,
            collectorId = this.collectorId,
            status = this.status,            // BackendOrderStatus
            pickupAddress = this.pickupAddress,
            location = this.location ?: emptyMap(),
            imgUrl1 = this.imgUrl1,
            imgUrl2 = this.imgUrl2,
            items = this.items,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
            // chưa có route từ /orders/{id}; để 0/null và sẽ giữ từ seed nếu có
            distanceKm = 0.0,
            travelTimeSeconds = null,
            travelDistanceMeters = null
        )
    }
}
