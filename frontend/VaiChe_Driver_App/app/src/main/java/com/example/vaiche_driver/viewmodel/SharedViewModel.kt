package com.example.vaiche_driver.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vaiche_driver.data.repository.OrderRepository
import com.example.vaiche_driver.model.DriverState
import com.example.vaiche_driver.model.NearbyOrderPublic
import com.example.vaiche_driver.model.OrderPublic
import com.example.vaiche_driver.model.Schedule
import com.example.vaiche_driver.model.localStatus
import com.example.vaiche_driver.model.toSchedule
import kotlinx.coroutines.launch

class SharedViewModel : ViewModel() {

    private val TAG = "SharedVM"
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

    // Sửa kiểu: NearbyOrderPublic (đúng với API getNearbyOrders)
    private val _foundNewOrder = MutableLiveData<Event<NearbyOrderPublic>>()
    val foundNewOrder: LiveData<Event<NearbyOrderPublic>> = _foundNewOrder

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
        Log.d(TAG, "findNearbyOrder(): START")
        val location = _workingLocation.value
        if (location == null) {
            _errorMessage.value = Event("Working location is not set. Please set a plan.")
            _driverState.value = DriverState.OFFLINE
            return
        }

        viewModelScope.launch {
            Log.d(TAG, "findNearbyOrder(): calling repo at lat=${location.first}, lon=${location.second}")
            val res: Result<List<NearbyOrderPublic>> =
                orderRepository.getNearbyOrders(location.first, location.second)

            res
                .onSuccess { nearbyOrders ->
                    Log.d(TAG, "findNearbyOrder(): DONE, size=${nearbyOrders.size}")
                    val newOrder = nearbyOrders.firstOrNull { it.id !in rejectedOrderIds }
                    if (newOrder != null) {
                        _foundNewOrder.value = Event(newOrder)
                    } else {
                        _errorMessage.value = Event("No new orders available in your area.")
                    }
                }
                .onFailure { e ->
                    Log.e(TAG, "findNearbyOrder(): ERROR ${e.message}", e)
                    _errorMessage.value = Event(e.message ?: "Failed to find nearby orders.")
                }
        }
    }

    /**
     * Tài xế chấp nhận một đơn hàng mới (dùng khi đã có Schedule).
     */
    fun onOrderAccepted(newOrder: Schedule) {
        _activeOrder.value = newOrder
        _driverState.value = DriverState.DELIVERING
        rejectedOrderIds.clear()
        _orderAcceptedEvent.value = Event(true)
    }

    /**
     * Overload: chấp nhận đơn trả về từ backend (OrderPublic) và tự map sang Schedule.
     * Dùng để fix lỗi "Argument type mismatch: OrderPublic vs Schedule".
     */
    fun onOrderAccepted(newOrder: OrderPublic) {
        val schedule = newOrder.toSchedule(newOrder.localStatus())
        onOrderAccepted(schedule)
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

    fun goOffline() {
        if (_driverState.value != DriverState.DELIVERING) {
            _driverState.value = DriverState.OFFLINE
        }
    }
}
