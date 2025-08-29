package com.example.vaiche_driver.viewmodel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.vaiche_driver.model.DriverState
import com.example.vaiche_driver.model.Schedule

/**
 * ViewModel này được chia sẻ (shared) giữa các Fragment và tồn tại xuyên suốt
 * vòng đời của Activity. Nó đóng vai trò là "nguồn chân lý duy nhất" (Single Source of Truth)
 * cho trạng thái chung của ứng dụng.
 */
class SharedViewModel : ViewModel() {

    // --- TRẠNG THÁI CỦA TÀI XẾ ---
    // `private` _driverState để chỉ ViewModel có thể thay đổi.
    private val _driverState = MutableLiveData<DriverState>(DriverState.OFFLINE)
    // `public` driverState để các Fragment có thể quan sát (observe).
    val driverState: LiveData<DriverState> = _driverState

    // --- ĐƠN HÀNG "UPCOMING" HIỆN TẠI ---
    private val _activeOrder = MutableLiveData<Schedule?>()
    val activeOrder: LiveData<Schedule?> = _activeOrder

    // --- DANH SÁCH ID ĐƠN HÀNG ĐÃ TỪ CHỐI ---
    private val rejectedOrderIds = mutableSetOf<String>()

    // --- KÊNH GIAO TIẾP CHO CÁC SỰ KIỆN DÙNG MỘT LẦN ---
    private val _orderAcceptedEvent = MutableLiveData<Event<Boolean>>()
    val orderAcceptedEvent: LiveData<Event<Boolean>> = _orderAcceptedEvent

    private val _orderRejectedEvent = MutableLiveData<Event<Boolean>>()
    val orderRejectedEvent: LiveData<Event<Boolean>> = _orderRejectedEvent

    /**
     * Cung cấp một bản sao chỉ đọc của danh sách ID đã từ chối.
     */
    fun getRejectedOrderIds(): Set<String> {
        return rejectedOrderIds
    }

    /**
     * Tài xế nhấn nút bật/tắt online.
     */
    fun toggleOnlineStatus() {
        if (_driverState.value == DriverState.OFFLINE) {
            _driverState.value = DriverState.ONLINE
        } else {
            // Chỉ có thể tắt khi không đang giao hàng
            if (_driverState.value != DriverState.DELIVERING) {
                _driverState.value = DriverState.OFFLINE
            }
        }
    }

    /**
     * Được gọi từ SetPlanDialogFragment khi tài xế xác nhận kế hoạch.
     */
    fun onPlanConfirmed() {
        _driverState.value = DriverState.FINDING_ORDER
    }

    /**
     * Được gọi từ NewOrderDialogFragment khi tài xế chấp nhận một đơn hàng mới.
     */
    fun onOrderAccepted(newOrder: Schedule) {
        _activeOrder.value = newOrder
        _driverState.value = DriverState.DELIVERING
        rejectedOrderIds.clear() // Xóa danh sách từ chối khi đã nhận đơn

        // Gửi tín hiệu "vừa chấp nhận" để Dashboard có thể mở MyScheduleDialogFragment
        _orderAcceptedEvent.value = Event(true)
    }

    /**
     * Được gọi từ NewOrderDialogFragment khi tài xế từ chối một đơn hàng.
     */
    fun onOrderRejected(orderId: String) {
        rejectedOrderIds.add(orderId)

        // Gửi tín hiệu "vừa từ chối" để Dashboard tiếp tục tìm kiếm
        _orderRejectedEvent.value = Event(true)
    }

    /**
     * Được gọi từ RatingsFragment khi tài xế đã hoàn thành toàn bộ quy trình.
     */
    fun onDeliveryFinished() {
        _activeOrder.value = null
        _driverState.value = DriverState.OFFLINE // Quay về trạng thái OFFLINE ban đầu
    }
}