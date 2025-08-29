package com.example.vaiche_driver.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.vaiche_driver.model.FakeDataSource
import com.example.vaiche_driver.model.OrderStatus
import com.example.vaiche_driver.model.ScheduleListItem

class ScheduleViewModel : ViewModel() {

    // --- LIVE DATA DUY NHẤT CHO TOÀN BỘ DANH SÁCH ---
    // Danh sách này chứa cả Header và ScheduleItem
    private val _scheduleList = MutableLiveData<List<ScheduleListItem>>()
    val scheduleList: LiveData<List<ScheduleListItem>> = _scheduleList

    // --- VỊ TRÍ CUỘN CỦA RECYCLERVIEW ---
    // Được sử dụng bởi ScheduleFragment để giữ lại trạng thái cuộn
    var scrollIndex = 0
    var scrollOffset = 0

    /**
     * Tải toàn bộ danh sách lịch trình, phân loại, và xây dựng một danh sách tổng hợp
     * để hiển thị trên RecyclerView.
     * TƯƠNG LAI: Sẽ gọi API `GET /api/schedules`.
     */
    fun loadSchedules() {
        // Lấy dữ liệu gốc từ DataSource
        val allSchedules = FakeDataSource.getSchedules()

        // Phân loại dữ liệu
        val upcomingOrder = allSchedules.find {
            it.status == OrderStatus.scheduled || it.status == OrderStatus.delivering
        }
        val completedOrders = allSchedules.filter { it.status == OrderStatus.completed }

        // Xây dựng danh sách tổng hợp để cung cấp cho Adapter
        val combinedList = mutableListOf<ScheduleListItem>()

        // Thêm section "Upcoming" nếu có đơn hàng
        if (upcomingOrder != null) {
            combinedList.add(ScheduleListItem.Header("Upcoming"))
            combinedList.add(ScheduleListItem.ScheduleItem(upcomingOrder))
        }

        // Thêm section "Completed" nếu có đơn hàng
        if (completedOrders.isNotEmpty()) {
            combinedList.add(ScheduleListItem.Header("Completed"))
            completedOrders.forEach { completedSchedule ->
                combinedList.add(ScheduleListItem.ScheduleItem(completedSchedule))
            }
        }

        // Cập nhật LiveData với danh sách tổng hợp cuối cùng.
        // Bất kỳ Fragment nào đang quan sát LiveData này sẽ nhận được dữ liệu mới.
        _scheduleList.value = combinedList
    }
}