package com.example.vaiche_driver.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.vaiche_driver.R
import com.example.vaiche_driver.adapter.ScheduleAdapter
import com.example.vaiche_driver.viewmodel.ScheduleViewModel
import com.example.vaiche_driver.viewmodel.SharedViewModel
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ScheduleFragment: Fragment() {

    // ViewModel của tab Schedule
    private val viewModel: ScheduleViewModel by activityViewModels()
    // SharedViewModel để nghe tín hiệu từ Dashboard/luồng đơn
    private val sharedVM: SharedViewModel by activityViewModels()

    private lateinit var scheduleAdapter: ScheduleAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutManager: LinearLayoutManager
    private var progressBar: ProgressBar? = null // ProgressBar có thể null nếu layout cha không có

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_schedule, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews(view)
        observeViewModel()
        observeSharedSignals() // ✅ lắng nghe tín hiệu từ SharedViewModel
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadSchedules()
    }

    override fun onPause() {
        super.onPause()
        if (::recyclerView.isInitialized) {
            viewModel.scrollIndex = layoutManager.findFirstVisibleItemPosition()
            val firstVisibleView = layoutManager.findViewByPosition(viewModel.scrollIndex)
            viewModel.scrollOffset = firstVisibleView?.top ?: 0
        }
    }

    private fun setupViews(view: View) {
        // Tìm ProgressBar trong layout cha (RelativeLayout)
        progressBar = view.findViewById(R.id.progress_bar) // Thêm ID này vào XML nếu chưa có

        recyclerView = view.findViewById(R.id.recycler_view_schedule)
        scheduleAdapter = ScheduleAdapter { clickedSchedule ->
            navigateToDetail(clickedSchedule.id)
        }

        layoutManager = LinearLayoutManager(context)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = scheduleAdapter
    }

    private fun observeViewModel() {
        // Lắng nghe danh sách tổng hợp
        viewModel.scheduleList.observe(viewLifecycleOwner) { list ->
            scheduleAdapter.submitList(list) {
                layoutManager.scrollToPositionWithOffset(
                    viewModel.scrollIndex,
                    viewModel.scrollOffset
                )
            }
        }

        // Lắng nghe trạng thái loading
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                progressBar?.visibility = View.VISIBLE
                recyclerView.visibility = View.INVISIBLE // Dùng INVISIBLE để giữ không gian layout
            } else {
                progressBar?.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            }
        }

        // Lắng nghe thông báo lỗi
        viewModel.errorMessage.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { message ->
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * ✅ Lắng nghe các tín hiệu từ SharedViewModel để reload Schedule:
     * - Khi có NewOrder (foundNewOrder): đôi khi BE cũng đẩy vào danh sách lịch/schedule → reload.
     * - Khi Accept (orderAcceptedEvent): chắc chắn lịch thay đổi → reload.
     * - (Tuỳ chọn) Khi hoàn tất đơn và về OFFLINE cũng có thể reload nếu bạn muốn.
     */
    private fun observeSharedSignals() {
        // New order tới → reload (kèm delay nhẹ để BE kịp ghi)
        sharedVM.foundNewOrder.observe(viewLifecycleOwner) { ev ->
            ev.getContentIfNotHandled()?.let {
                viewLifecycleOwner.lifecycleScope.launch {
                    delay(400) // nhẹ, đủ cho backend cập nhật
                    viewModel.loadSchedules()
                }
            }
        }

        // Sau khi Accept → chắc chắn có lịch mới
        sharedVM.orderAcceptedEvent.observe(viewLifecycleOwner) { ev ->
            if (ev.getContentIfNotHandled() == true) {
                viewLifecycleOwner.lifecycleScope.launch {
                    delay(300)
                    viewModel.loadSchedules()
                }
            }
        }

        // (Tuỳ chọn) Khi kết thúc giao hàng → refresh lịch để Completed cập nhật
        // Nếu bạn có bắn event riêng khi finish thì nghe ở đây; hiện tại có thể dựa theo driverState:
        sharedVM.driverState.observe(viewLifecycleOwner) { state ->
            // Nếu bạn muốn, có thể reload khi chuyển về OFFLINE/ONLINE
            // Ví dụ:
            // if (state == DriverState.OFFLINE) {
            //     viewLifecycleOwner.lifecycleScope.launch {
            //         delay(300)
            //         viewModel.loadSchedules()
            //     }
            // }
        }
    }

    private fun navigateToDetail(orderId: String) {
        val detailFragment = OrderDetailFragment.newInstance(orderId)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, detailFragment)
            .addToBackStack(null)
            .commit()
    }
}
