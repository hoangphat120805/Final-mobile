package com.example.vaiche_driver.fragment

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.vaiche_driver.R
import com.example.vaiche_driver.adapter.BottomNavHelper
import com.example.vaiche_driver.adapter.BottomNavScreen
import com.example.vaiche_driver.model.DriverState
import com.example.vaiche_driver.model.FakeDataSource
import com.example.vaiche_driver.viewmodel.SharedViewModel

/**
 * Fragment này là màn hình chính (Dashboard), đóng vai trò là "Trung tâm điều khiển".
 * Nó lắng nghe trạng thái từ SharedViewModel và quyết định hành động tiếp theo.
 * Nó cũng tự quản lý thanh điều hướng dưới cùng của riêng mình.
 */
class DashboardFragment : Fragment() {

    // Truy cập SharedViewModel chung của Activity
    private val sharedViewModel: SharedViewModel by activityViewModels()

    // Handler để giả lập việc tìm kiếm đơn hàng trong nền
    private val findingHandler = Handler(Looper.getMainLooper())
    private var findingRunnable: Runnable? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Lắng nghe các thay đổi từ ViewModel
        observeViewModel()

        // Xử lý các sự kiện click trên giao diện
        setupClickListeners(view)
    }

    private fun setupClickListeners(view: View) {
        val tvCenterTitle = view.findViewById<TextView>(R.id.tvCenterTitle)
        tvCenterTitle.setOnClickListener {
            sharedViewModel.toggleOnlineStatus()
        }

        view.findViewById<ImageView>(R.id.btnLeft).setOnClickListener { /* TODO: Navigate to Earnings */ }
        view.findViewById<ImageView>(R.id.btnRight).setOnClickListener { /* TODO: Navigate to Notifications */ }

    }

    private fun observeViewModel() {
        // Sử dụng view? để đảm bảo an toàn nếu Fragment bị hủy bất ngờ
        val tvCenterTitle = view?.findViewById<TextView>(R.id.tvCenterTitle) ?: return

        // Lắng nghe trạng thái chung của tài xế
        sharedViewModel.driverState.observe(viewLifecycleOwner) { state ->
            stopFindingOrder() // Luôn dừng hành động tìm kiếm cũ trước khi xử lý trạng thái mới

            when (state) {
                DriverState.OFFLINE -> {
                    tvCenterTitle.text = "Offline"
                    tvCenterTitle.isEnabled = true
                }
                DriverState.ONLINE -> {
                    tvCenterTitle.text = "Online"
                    // Chỉ mở dialog nếu nó chưa được mở
                    if (parentFragmentManager.findFragmentByTag("SetPlanDialog") == null) {
                        SetPlanDialogFragment().show(parentFragmentManager, "SetPlanDialog")
                    }
                }
                DriverState.FINDING_ORDER -> {
                    tvCenterTitle.text = "Finding..."
                    tvCenterTitle.isEnabled = false
                    // `startFindingOrder` sẽ được kích hoạt bởi sự kiện từ SetPlanDialog
                    // hoặc sự kiện từ chối đơn hàng.
                    startFindingOrder()
                }
                DriverState.DELIVERING -> {
                    tvCenterTitle.text = "Delivering"
                    tvCenterTitle.isEnabled = false
                }
            }
        }

        // Lắng nghe sự kiện "vừa chấp nhận đơn hàng" để mở MySchedule
        sharedViewModel.orderAcceptedEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let {
                if (parentFragmentManager.findFragmentByTag("MyScheduleDialog") == null) {
                    MyScheduleDialogFragment().show(parentFragmentManager, "MyScheduleDialog")
                }
            }
        }

        // Lắng nghe sự kiện từ chối đơn hàng để tiếp tục tìm kiếm
        sharedViewModel.orderRejectedEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let {
                Toast.makeText(context, "Finding another order...", Toast.LENGTH_SHORT).show()
                SuccessDialogFragment().show(parentFragmentManager, SuccessDialogFragment.TAG)
                startFindingOrder(immediate = true)
            }
        }
    }

    /**
     * Bắt đầu quá trình giả lập việc tìm kiếm đơn hàng trong nền.
     */
    private fun startFindingOrder(immediate: Boolean = false) {
        findingRunnable = Runnable {
            val rejectedIds = sharedViewModel.getRejectedOrderIds()
            val newOrder = FakeDataSource.getPendingOrder(rejectedIds)

            if (newOrder != null && isAdded) { // isAdded để chắc chắn Fragment vẫn còn tồn tại
                (parentFragmentManager.findFragmentByTag(SuccessDialogFragment.TAG) as? DialogFragment)?.dismiss()
                NewOrderDialogFragment.newInstance(newOrder.id).show(parentFragmentManager, "NewOrderDialog")
            } else {
                Toast.makeText(context, "No new orders found, trying again...", Toast.LENGTH_SHORT).show()
                if (isAdded) { // Chỉ post delay nếu fragment vẫn còn tồn tại
                    findingHandler.postDelayed(findingRunnable!!, 5000)
                }
            }
        }
        val delay = if (immediate) 0L else 3000L
        findingHandler.postDelayed(findingRunnable!!, delay)
    }

    /**
     * Dừng quá trình tìm kiếm.
     */
    private fun stopFindingOrder() {
        findingRunnable?.let { findingHandler.removeCallbacks(it) }
        findingRunnable = null
    }


    override fun onDestroyView() {
        super.onDestroyView()
        // Dọn dẹp handler để tránh memory leak
        stopFindingOrder()
    }
}