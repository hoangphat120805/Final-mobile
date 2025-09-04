package com.example.vaiche_driver.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vaiche_driver.data.repository.OrderRepository
import com.example.vaiche_driver.model.OrderPublic
import com.example.vaiche_driver.model.Schedule
import com.example.vaiche_driver.model.toSchedule
import com.example.vaiche_driver.model.DriverState
import kotlinx.coroutines.launch

class SharedViewModel : ViewModel() {

    // Khởi tạo Repository
    private val orderRepository = OrderRepository()

    private val _workingLocation = MutableLiveData<Pair<Double, Double>?>()
    val workingLocation: LiveData<Pair<Double, Double>?> = _workingLocation

    // --- CÁC LIVE DATA CHO TRẠNG THÁI GIAO DIỆN ---
    private val _driverState = MutableLiveData<DriverState>(DriverState.OFFLINE)
    val driverState: LiveData<DriverState> = _driverState

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<Event<String>>()
    val errorMessage: LiveData<Event<String>> = _errorMessage

    // --- CÁC LIVE DATA CHO DỮ LIỆU ---
    private val _activeOrder = MutableLiveData<Schedule?>()
    val activeOrder: LiveData<Schedule?> = _activeOrder

    private val _foundNewOrder = MutableLiveData<Event<OrderPublic>>()
    val foundNewOrder: LiveData<Event<OrderPublic>> = _foundNewOrder

    private val rejectedOrderIds = mutableSetOf<String>()

    // --- KÊNH GIAO TIẾP CHO SỰ KIỆN DÙNG MỘT LẦN ---
    private val _orderAcceptedEvent = MutableLiveData<Event<Boolean>>()
    val orderAcceptedEvent: LiveData<Event<Boolean>> = _orderAcceptedEvent

    private val _orderRejectedEvent = MutableLiveData<Event<Boolean>>()
    val orderRejectedEvent: LiveData<Event<Boolean>> = _orderRejectedEvent

    fun getRejectedOrderIds(): Set<String> = rejectedOrderIds

    // --- CÁC HÀM XỬ LÝ LOGIC ---

    fun toggleOnlineStatus() {
        if (_driverState.value == DriverState.OFFLINE) {
            _driverState.value = DriverState.ONLINE
        } else if (_driverState.value != DriverState.DELIVERING) {
            _driverState.value = DriverState.OFFLINE
        }
    }

    fun onPlanConfirmed() {
        _driverState.value = DriverState.FINDING_ORDER
    }

    fun onPlanConfirmed(lat: Double, lng: Double) {
        _workingLocation.value = Pair(lat, lng)
        _driverState.value = DriverState.FINDING_ORDER
    }

    /**
     * Tìm kiếm một đơn hàng mới ở gần.
     */
    fun findNearbyOrder() {
        // 1. Lấy vị trí làm việc đã được lưu trong LiveData `_workingLocation`.
        //    Nếu chưa có vị trí nào được set (rất hiếm), thoát ra để tránh lỗi.
        val location = _workingLocation.value
        if (location == null) {
            _errorMessage.value = Event("Working location is not set. Please set a plan.")
            // Chuyển trạng thái về OFFLINE để người dùng làm lại
            _driverState.value = DriverState.OFFLINE
            return
        }
        // 3. Sử dụng viewModelScope để khởi chạy một coroutine an toàn.
        viewModelScope.launch {

            // 4. Gọi hàm trong Repository để lấy dữ liệu từ API
            //    sử dụng vĩ độ (location.first) và kinh độ (location.second).
            val result = orderRepository.getNearbyOrders(location.first, location.second)

            // 5. Xử lý kết quả trả về từ Repository
            result.onSuccess { nearbyOrders ->
                // 5a. Nếu gọi API thành công, lọc ra đơn hàng đầu tiên
                //     mà tài xế chưa từ chối.
                val newOrder = nearbyOrders.find { it.id !in rejectedOrderIds }

                if (newOrder != null) {
                    // Nếu tìm thấy một đơn hàng phù hợp, gửi nó đến Fragment
                    // thông qua một LiveData Event.
                    _foundNewOrder.value = Event(newOrder)
                } else {
                    // Nếu có đơn hàng trả về nhưng tất cả đều đã bị từ chối,
                    // hoặc không có đơn hàng nào, gửi thông báo lỗi.
                    _errorMessage.value = Event("No new orders available in your area.")
                }
            }.onFailure { error ->
                // 5b. Nếu gọi API thất bại (ví dụ: mất mạng, server lỗi),
                //     gửi thông báo lỗi cho Fragment.
                _errorMessage.value = Event(error.message ?: "Failed to find nearby orders.")
            }
        }
    }

    /**
     * Tài xế chấp nhận một đơn hàng mới.
     */
    fun onOrderAccepted(newOrder: Schedule) {
        _activeOrder.value = newOrder
        _driverState.value = DriverState.DELIVERING
        rejectedOrderIds.clear()
        _orderAcceptedEvent.value = Event(true)
    }



    /**
     * Tài xế từ chối một đơn hàng.
     */
    fun onOrderRejected(orderId: String) {
        rejectedOrderIds.add(orderId)
        _orderRejectedEvent.value = Event(true)
    }

    /**
     * Tài xế đã hoàn thành toàn bộ quy trình.
     */
    fun onDeliveryFinished() {
        _activeOrder.value = null
        _driverState.value = DriverState.OFFLINE
    }
}