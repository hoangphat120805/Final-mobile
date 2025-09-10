package com.example.vaiche_driver.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vaiche_driver.data.network.RetrofitClient
import com.example.vaiche_driver.data.repository.OrderRepository
import com.example.vaiche_driver.model.OrderStatus
import com.example.vaiche_driver.model.Schedule
import com.example.vaiche_driver.model.ScheduleListItem
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class ScheduleViewModel : ViewModel() {

    // Khởi tạo Repository
    private val orderRepository = OrderRepository { RetrofitClient.instance }

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

    fun loadSchedules() {
        _isLoading.value = true
        viewModelScope.launch {
            val result = orderRepository.getSchedules()
            result.onSuccess { allSchedules ->
                // helper
                fun Schedule.isUpcoming(): Boolean =
                    status == OrderStatus.scheduled || status == OrderStatus.delivering

                fun Schedule.sortKeyMillis(): Long {
                    // date: dd/MM/yyyy, time: HH:mm
                    return try {
                        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                        sdf.parse("${date} ${time}")?.time ?: 0L
                    } catch (_: Exception) {
                        0L
                    }
                }

                // Upcoming: lấy cái "mới nhất" (theo thời điểm tạo/accept bạn đã map)
                val upcomingOrder: Schedule? = allSchedules
                    .filter { it.isUpcoming() }
                    .maxByOrNull { it.sortKeyMillis() }

                // Completed: sort mới nhất -> cũ
                val completedOrders: List<Schedule> = allSchedules
                    .filter { it.status == OrderStatus.completed }
                    .sortedByDescending { it.sortKeyMillis() }

                // Build list hiển thị
                val combined = buildList {
                    if (upcomingOrder != null) {
                        add(ScheduleListItem.Header("Upcoming"))
                        add(ScheduleListItem.ScheduleItem(upcomingOrder))
                    }
                    if (completedOrders.isNotEmpty()) {
                        add(ScheduleListItem.Header("Completed"))
                        completedOrders.forEach { add(ScheduleListItem.ScheduleItem(it)) }
                    }
                }

                _scheduleList.value = combined
            }.onFailure { err ->
                _errorMessage.value = Event(err.message ?: "Failed to load schedules.")
            }
            _isLoading.value = false
        }
    }
}
