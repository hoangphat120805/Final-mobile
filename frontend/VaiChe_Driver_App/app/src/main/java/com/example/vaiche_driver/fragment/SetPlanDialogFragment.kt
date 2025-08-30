package com.example.vaiche_driver.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import com.example.vaiche_driver.R
import com.example.vaiche_driver.model.FakeDataSource
import com.example.vaiche_driver.model.Schedule
import com.example.vaiche_driver.model.OrderStatus
import com.example.vaiche_driver.viewmodel.SharedViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class SetPlanDialogFragment : BottomSheetDialogFragment() {

    // Truy cập SharedViewModel chung của Activity
    private val sharedViewModel: SharedViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_set_plan, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // --- TÌM CÁC VIEW CẦN TƯƠƠNG TÁC ---
        val cardView = view.findViewById<View>(R.id.latest_schedule_card)
        val confirmButton = view.findViewById<Button>(R.id.btn_confirm)
        val seeAllTextView = view.findViewById<TextView>(R.id.tv_see_all)

        // --- BIND DỮ LIỆU CHO LỊCH TRÌNH GẦN NHẤT ---
        // Lấy danh sách completed và lấy phần tử đầu tiên (gần nhất)
        val latestCompleted = FakeDataSource.getSchedules()
            .filter { it.status == OrderStatus.completed }
            .firstOrNull()

        if (latestCompleted != null) {
            cardView.visibility = View.VISIBLE
            bindCardData(cardView, latestCompleted)

            // Gán sự kiện click cho card để mở chi tiết
            cardView.setOnClickListener {
                dismiss() // Đóng dialog hiện tại
                navigateToDetailFragment(latestCompleted.id) // Mở màn hình chi tiết
            }
        } else {
            // Nếu không có lịch trình nào đã hoàn thành, ẩn card đi
            cardView.visibility = View.GONE
        }

        // --- XỬ LÝ CÁC SỰ KIỆN CLICK ---
        confirmButton.setOnClickListener {
            // 1. Báo cáo lên ViewModel để chuyển trạng thái thành FINDING_ORDER
            sharedViewModel.onPlanConfirmed()

            // 2. Mở dialog "Waiting..."
            SuccessDialogFragment().show(parentFragmentManager, SuccessDialogFragment.TAG)

            // 3. Đóng dialog SetPlan này lại
            dismiss()
        }

        seeAllTextView.setOnClickListener {
            dismiss() // Đóng dialog hiện tại
            navigateToScheduleFragment() // Mở màn hình danh sách đầy đủ
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
        val dateText = cardView.findViewById<TextView>(R.id.tv_schedule_date)
        val timeText = cardView.findViewById<TextView>(R.id.tv_schedule_time)
        val orderIdText = cardView.findViewById<TextView>(R.id.tv_order_id)
        val statusText = cardView.findViewById<TextView>(R.id.tv_status)
        val startName = cardView.findViewById<TextView>(R.id.tv_start_location_name)
        val startAddress = cardView.findViewById<TextView>(R.id.tv_start_location_address)
        val endName = cardView.findViewById<TextView>(R.id.tv_end_location_name)
        val endAddress = cardView.findViewById<TextView>(R.id.tv_end_location_address)

        dateText.text = schedule.date
        timeText.text = "${schedule.time},"
        orderIdText.text = "#${schedule.id.takeLast(4)}"
        startName.text = schedule.startLocationName
        startAddress.text = schedule.startLocationAddress
        endName.text = schedule.endLocationName
        endAddress.text = schedule.endLocationAddress

        // Card này chỉ hiển thị trạng thái "Done"
        statusText.text = "Done"
        statusText.background = ContextCompat.getDrawable(requireContext(), R.drawable.status_done_background)
        statusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.blue_text_color))
    }
}