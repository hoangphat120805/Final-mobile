package com.example.vaiche_driver.viewmodel

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vaiche_driver.data.repository.OrderRepository
import com.example.vaiche_driver.fragment.OrderDetailFragment
import com.example.vaiche_driver.model.OrderDetail
import com.example.vaiche_driver.model.OrderCompletionRequest
import kotlinx.coroutines.launch

class OrderDetailViewModel : ViewModel() {

    // Khởi tạo Repository để tương tác với nguồn dữ liệu
    private val orderRepository = OrderRepository()

    // --- CÁC LIVE DATA CHO GIAO DIỆN ---
    private val _orderDetail = MutableLiveData<OrderDetail?>()
    val orderDetail: LiveData<OrderDetail?> = _orderDetail

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<Event<String>>()
    val errorMessage: LiveData<Event<String>> = _errorMessage

    // LiveData dạng Event để thông báo cho Fragment khi đơn hàng đã hoàn thành
    private val _orderCompletedEvent = MutableLiveData<Event<Boolean>>()
    val orderCompletedEvent: LiveData<Event<Boolean>> = _orderCompletedEvent

    /**
     * Tải dữ liệu chi tiết của đơn hàng từ Repository.
     */
    fun loadOrder(orderId: String?) {
        if (orderId == null) {
            _errorMessage.value = Event("Order ID is missing.")
            return
        }
        _isLoading.value = true
        viewModelScope.launch {
            val result = orderRepository.getOrderDetail(orderId)
            result.onSuccess { orderDetail ->
                _orderDetail.value = orderDetail
            }.onFailure { error ->
                _errorMessage.value = Event(error.message ?: "Failed to load order details.")
            }
            _isLoading.value = false
        }
    }

    /**
     * Xử lý sau khi chụp ảnh thành công.
     * TƯƠNG LAI: Sẽ gọi API upload ảnh.
     */
    fun onPhotoTaken(uri: Uri, target: OrderDetailFragment.PhotoTarget) {
        val currentOrder = _orderDetail.value ?: return

        _isLoading.value = true
        viewModelScope.launch {
            // TODO: Gọi repository.uploadPhoto(currentOrder.id, uri, target)
            // Sau khi upload thành công, API sẽ trả về Order mới đã có URL ảnh
            // Hiện tại, chúng ta chỉ cần tải lại dữ liệu để giả lập
            loadOrder(currentOrder.id)
            _isLoading.value = false
        }
    }

    /**
     * Xử lý khi người dùng xác nhận đã Pick-Up.
     * Chỉ cập nhật trạng thái ở client thành "delivering".
     */
    fun onPickupConfirmed() {
        val currentOrder = _orderDetail.value ?: return
        _isLoading.value = true
        viewModelScope.launch {
            val result = orderRepository.markAsDelivering(currentOrder.id)
            result.onSuccess {
                // Tải lại dữ liệu để giao diện được cập nhật với trạng thái "delivering" mới
                loadOrder(currentOrder.id)
            }.onFailure { error ->
                _errorMessage.value = Event(error.message ?: "Failed to update status.")
            }
            // Không cần ẩn isLoading ở đây vì loadOrder đã xử lý
        }
    }

    /**
     * Xử lý khi người dùng xác nhận đã Drop-Off và hoàn thành đơn.
     * Sẽ gọi API để cập nhật trạng thái "completed" trên backend.
     */
    fun onDeliveryCompleted() {
        val currentOrder = _orderDetail.value ?: return

        _isLoading.value = true
        viewModelScope.launch {
            // TODO: Xây dựng request body thực tế từ dữ liệu đơn hàng (các item đã được cân lại)
            val requestBody = OrderCompletionRequest(items = emptyList())

            val result = orderRepository.completeOrder(currentOrder.id, requestBody)

            result.onSuccess {
                // Gửi tín hiệu cho Fragment biết rằng đã hoàn thành thành công
                _orderCompletedEvent.value = Event(true)
            }.onFailure { error ->
                _errorMessage.value = Event(error.message ?: "Failed to complete order.")
            }
            _isLoading.value = false
        }
    }
}