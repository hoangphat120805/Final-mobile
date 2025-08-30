package com.example.vaiche_driver.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import com.example.vaiche_driver.R
import com.example.vaiche_driver.model.Schedule
import com.example.vaiche_driver.model.OrderStatus
import com.example.vaiche_driver.viewmodel.SharedViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.card.MaterialCardView

class MyScheduleDialogFragment : BottomSheetDialogFragment() {

    // Truy cập SharedViewModel chung của Activity
    private val sharedViewModel: SharedViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_my_schedule, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val cardView = view.findViewById<MaterialCardView>(R.id.card_schedule)
        val seeAllTextView = view.findViewById<TextView>(R.id.tv_see_all)
        // Bạn có thể thêm một TextView cho thông báo "No active schedule" trong layout
        // val noScheduleText = view.findViewById<TextView>(R.id.tv_no_schedule)

        // Lắng nghe đơn hàng đang hoạt động từ ViewModel
        sharedViewModel.activeOrder.observe(viewLifecycleOwner) { activeOrder ->
            if (activeOrder != null) {
                // Nếu có đơn hàng, bind dữ liệu và gán sự kiện click
                cardView.visibility = View.VISIBLE
                // noScheduleText.visibility = View.GONE

                bindCardData(cardView, activeOrder)

                cardView.setOnClickListener {
                    dismiss()
                    navigateToDetailFragment(activeOrder.id)
                }
            } else {
                // Nếu không có đơn hàng nào đang hoạt động, ẩn card và hiển thị thông báo
                cardView.visibility = View.GONE
                // noScheduleText.visibility = View.VISIBLE
            }
        }

        // Luôn gán sự kiện cho "See all"
        seeAllTextView.setOnClickListener {
            dismiss()
            navigateToScheduleFragment()
        }
    }

    /**
     * Hàm điều hướng đến ScheduleFragment (danh sách đầy đủ).
     */
    private fun navigateToScheduleFragment() {
        val scheduleFragment = ScheduleFragment()
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, scheduleFragment) // <-- NHỚ THAY ID CONTAINER CỦA BẠN
            .addToBackStack(null)
            .commit()
    }

    /**
     * Hàm điều hướng đến OrderDetailFragment (chi tiết đơn hàng).
     */
    private fun navigateToDetailFragment(orderId: String) {
        val detailFragment = OrderDetailFragment.newInstance(orderId)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, detailFragment) // <-- NHỚ THAY ID CONTAINER CỦA BẠN
            .addToBackStack(null)
            .commit()
    }

    /**
     * Hàm tiện ích để điền dữ liệu vào card.
     */
    private fun bindCardData(cardView: View, schedule: Schedule) {
        // Tìm các View bên trong card
        val dateText = cardView.findViewById<TextView>(R.id.tv_schedule_date)
        val timeText = cardView.findViewById<TextView>(R.id.tv_schedule_time)
        val orderIdText = cardView.findViewById<TextView>(R.id.tv_order_id)
        val statusText = cardView.findViewById<TextView>(R.id.tv_status)
        val startName = cardView.findViewById<TextView>(R.id.tv_start_location_name)
        val startAddress = cardView.findViewById<TextView>(R.id.tv_start_location_address)
        val endName = cardView.findViewById<TextView>(R.id.tv_end_location_name)
        val endAddress = cardView.findViewById<TextView>(R.id.tv_end_location_address)

        // Gán dữ liệu
        dateText.text = schedule.date
        timeText.text = "${schedule.time},"
        orderIdText.text = "#${schedule.id.takeLast(4)}"
        startName.text = schedule.startLocationName
        startAddress.text = schedule.startLocationAddress
        endName.text = schedule.endLocationName
        endAddress.text = schedule.endLocationAddress

        // Logic `when` để hiển thị đúng trạng thái
        when (schedule.status) {
            OrderStatus.scheduled -> {
                statusText.text = "Scheduled"
                statusText.background = ContextCompat.getDrawable(requireContext(), R.drawable.status_scheduled_background)
                statusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.green_text_color))
            }
            OrderStatus.delivering -> {
                statusText.text = "Delivering"
                statusText.background = ContextCompat.getDrawable(requireContext(), R.drawable.status_delivering_background)
                statusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_delivering_text))
            }
            else -> {
                // Các trạng thái khác (completed, pending...) không phải là "Upcoming"
                // nên sẽ không hiển thị trong dialog này.
                statusText.visibility = View.GONE
            }
        }
    }
}