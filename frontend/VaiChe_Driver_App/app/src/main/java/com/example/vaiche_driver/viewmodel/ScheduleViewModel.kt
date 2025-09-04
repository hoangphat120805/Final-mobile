package com.example.vaiche_driver.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vaiche_driver.data.repository.OrderRepository
import com.example.vaiche_driver.model.OrderStatus
import com.example.vaiche_driver.model.ScheduleListItem
import kotlinx.coroutines.launch

class ScheduleViewModel : ViewModel() {

    // Khởi tạo Repository
    private val orderRepository = OrderRepository()

    // --- CÁC LIVE DATA CHO GIAO DIỆN ---
    private val _scheduleList = MutableLiveData<List<ScheduleListItem>>()
    val scheduleList: LiveData<List<ScheduleListItem>> = _scheduleList

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<Event<String>>()
    val errorMessage: LiveData<Event<String>> = _errorMessage

    // --- VỊ TRÍ CUỘN CỦA RECYCLERVIEW ---
    var scrollIndex = 0
    var scrollOffset = 0

    /**
     * Tải toàn bộ danh sách lịch trình từ Repository (API).
     */
    fun loadSchedules() {
        // Hiển thị ProgressBar trên giao diện
        _isLoading.value = true

        // Sử dụng viewModelScope để khởi chạy một coroutine an toàn
        viewModelScope.launch {
            val result = orderRepository.getSchedules()

            // Xử lý kết quả trả về từ Repository
            result.onSuccess { allSchedules ->
                // Phân loại dữ liệu nhận về từ API
                val upcomingOrder = allSchedules.find {
                    it.status == OrderStatus.scheduled || it.status == OrderStatus.delivering
                }
                val completedOrders = allSchedules.filter { it.status == OrderStatus.completed }

                // Xây dựng danh sách tổng hợp
                val combinedList = mutableListOf<ScheduleListItem>()
                if (upcomingOrder != null) {
                    combinedList.add(ScheduleListItem.Header("Upcoming"))
                    combinedList.add(ScheduleListItem.ScheduleItem(upcomingOrder))
                }
                if (completedOrders.isNotEmpty()) {
                    combinedList.add(ScheduleListItem.Header("Completed"))
                    completedOrders.forEach { completedSchedule ->
                        combinedList.add(ScheduleListItem.ScheduleItem(completedSchedule))
                    }
                }

                // Cập nhật LiveData với danh sách cuối cùng
                _scheduleList.value = combinedList
            }.onFailure { error ->
                // Nếu có lỗi, gửi thông báo lỗi cho giao diện
                _errorMessage.value = Event(error.message ?: "An unknown error occurred")
            }

            // Ẩn ProgressBar sau khi đã xử lý xong
            _isLoading.value = false
        }
    }
}