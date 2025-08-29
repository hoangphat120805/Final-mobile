package com.example.vaiche_driver.fragment // Gói (package) được đề xuất

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.vaiche_driver.R
import com.example.vaiche_driver.adapter.BottomNavHelper
import com.example.vaiche_driver.adapter.BottomNavScreen
import com.example.vaiche_driver.adapter.ScheduleAdapter
import com.example.vaiche_driver.viewmodel.ScheduleViewModel

class ScheduleFragment: Fragment() {

    // Sử dụng activityViewModels để ViewModel tồn tại qua các lần replace Fragment
    private val viewModel: ScheduleViewModel by activityViewModels()

    private lateinit var scheduleAdapter: ScheduleAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutManager: LinearLayoutManager

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
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadSchedules()
    }

    override fun onPause() {
        super.onPause()
        if (::recyclerView.isInitialized) {
            // Lưu lại vị trí chính xác
            viewModel.scrollIndex = layoutManager.findFirstVisibleItemPosition()
            val firstVisibleView = layoutManager.findViewByPosition(viewModel.scrollIndex)
            viewModel.scrollOffset = firstVisibleView?.top ?: 0
        }
    }

    private fun setupViews(view: View) {
        recyclerView = view.findViewById(R.id.recycler_view_schedule)
        scheduleAdapter = ScheduleAdapter { clickedSchedule ->
            navigateToDetail(clickedSchedule.id)
        }

        layoutManager = LinearLayoutManager(context)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = scheduleAdapter
    }

    /**
     * --- THAY ĐỔI QUAN TRỌNG NHẤT NẰM Ở ĐÂY ---
     * Sử dụng OnLayoutChangeListener để đảm bảo khôi phục vị trí vào đúng thời điểm.
     */
    private fun observeViewModel() {
        viewModel.scheduleList.observe(viewLifecycleOwner) { list ->
            // 1. Gửi danh sách mới cho Adapter
            scheduleAdapter.submitList(list)

            // 2. Thêm một Listener để đợi cho đến khi RecyclerView đã vẽ xong layout mới
            val layoutListener = object : View.OnLayoutChangeListener {
                override fun onLayoutChange(
                    v: View?, left: Int, top: Int, right: Int, bottom: Int,
                    oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
                ) {
                    // 3. Sau khi layout đã ổn định, gỡ Listener này đi để nó không chạy lại
                    recyclerView.removeOnLayoutChangeListener(this)

                    // 4. BÂY GIỜ mới là thời điểm an toàn và chính xác nhất để khôi phục vị trí
                    layoutManager.scrollToPositionWithOffset(viewModel.scrollIndex, viewModel.scrollOffset)
                }
            }
            recyclerView.addOnLayoutChangeListener(layoutListener)
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