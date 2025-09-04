package com.example.vaiche_driver.fragment

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.vaiche_driver.R
import com.example.vaiche_driver.adapter.BottomNavHelper
import com.example.vaiche_driver.adapter.BottomNavScreen
import com.example.vaiche_driver.model.DriverState
import com.example.vaiche_driver.model.FakeDataSource
import com.example.vaiche_driver.ui.SetPlanDialogFragment
import com.example.vaiche_driver.viewmodel.SharedViewModel
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.locationcomponent.location

/**
 * Fragment này là màn hình chính (Dashboard), đóng vai trò là "Trung tâm điều khiển".
 * Nó lắng nghe trạng thái từ SharedViewModel và quyết định hành động tiếp theo.
 * Nó cũng tự quản lý thanh điều hướng dưới cùng của riêng mình.
 */
class DashboardFragment : Fragment() {

    private val sharedViewModel: SharedViewModel by activityViewModels()

    private var mapView: MapView? = null

    private val findingHandler = Handler(Looper.getMainLooper())
    private var findingRunnable: Runnable? = null

    // Launcher để xin quyền truy cập vị trí
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                initLocationComponent()
            } else {
                Toast.makeText(context, "Location permission is required for the map feature.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapView = view.findViewById(R.id.mapView)

        // Khởi tạo bản đồ và các thành phần
        mapView?.getMapboxMap()?.loadStyleUri(Style.MAPBOX_STREETS) {
            checkLocationPermission()
        }

        observeViewModel()
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
        val tvCenterTitle = view?.findViewById<TextView>(R.id.tvCenterTitle) ?: return

        sharedViewModel.driverState.observe(viewLifecycleOwner) { state ->
            stopFindingOrder()
            when (state) {
                DriverState.OFFLINE -> {
                    tvCenterTitle.text = "Offline"
                    tvCenterTitle.isEnabled = true
                }
                DriverState.ONLINE -> {
                    tvCenterTitle.text = "Online"
                    if (parentFragmentManager.findFragmentByTag("SetPlanDialog") == null) {
                        SetPlanDialogFragment().show(parentFragmentManager, "SetPlanDialog")
                    }
                }
                DriverState.FINDING_ORDER -> {
                    tvCenterTitle.text = "Finding..."
                    tvCenterTitle.isEnabled = false
                    // Start finding order will be triggered by onPlanConfirmed in ViewModel
                }
                DriverState.DELIVERING -> {
                    tvCenterTitle.text = "Delivering"
                    tvCenterTitle.isEnabled = false
                }
            }
        }

        sharedViewModel.orderAcceptedEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let {
                if (parentFragmentManager.findFragmentByTag("MyScheduleDialog") == null) {
                    MyScheduleDialogFragment().show(parentFragmentManager, "MyScheduleDialog")
                }
            }
        }

        sharedViewModel.orderRejectedEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let {
                Toast.makeText(context, "Finding another order...", Toast.LENGTH_SHORT).show()
                SuccessDialogFragment().show(parentFragmentManager, SuccessDialogFragment.TAG)
                startFindingOrder(immediate = true)
            }
        }

        sharedViewModel.foundNewOrder.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { newOrder ->
                stopFindingOrder()
                (parentFragmentManager.findFragmentByTag(SuccessDialogFragment.TAG) as? DialogFragment)?.dismiss()
                NewOrderDialogFragment.newInstance(newOrder.id).show(parentFragmentManager, "NewOrderDialog")
                // TODO: Vẽ marker và đường đi cho newOrder trên bản đồ
            }
        }
    }

    private fun startFindingOrder(immediate: Boolean = false) {
        // 1. Định nghĩa công việc cần làm (tìm kiếm đơn hàng)
        findingRunnable = Runnable {
            // 2. Kiểm tra xem Fragment có còn "sống" không để tránh crash
            if (!isAdded) return@Runnable

            // 3. Ra lệnh cho ViewModel tìm đơn hàng.
            //    ViewModel đã có sẵn vị trí làm việc và danh sách từ chối.
            sharedViewModel.findNearbyOrder()

            // 4. Lên lịch để tự động tìm lại sau một khoảng thời gian.
            //    Điều này tạo ra một vòng lặp tìm kiếm.
            //    Khi một đơn hàng được tìm thấy hoặc trạng thái thay đổi,
            //    hàm `stopFindingOrder()` sẽ được gọi để ngắt vòng lặp này.
            findingHandler.postDelayed(findingRunnable!!, 5000) // Tìm lại sau mỗi 15 giây
        }

        // 5. Quyết định độ trễ cho lần tìm kiếm đầu tiên
        val initialDelay = if (immediate) 0L else 3000L // 0 giây nếu reject, 3 giây nếu là lần đầu

        // 6. Bắt đầu thực thi công việc sau khoảng trễ
        findingHandler.postDelayed(findingRunnable!!, initialDelay)
    }

    private fun stopFindingOrder() {
        findingRunnable?.let { findingHandler.removeCallbacks(it) }
        findingRunnable = null
    }

    private fun navigateTo(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    // --- CÁC HÀM HỖ TRỢ MAPBOX ---
    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            initLocationComponent()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun initLocationComponent() {
        val locationComponentPlugin = mapView?.location
        locationComponentPlugin?.updateSettings {
            this.enabled = true
            // TODO: Tùy chỉnh icon vị trí nếu cần
        }
    }

    // --- QUẢN LÝ VÒNG ĐỜI CỦA MAPVIEW ---
    override fun onStart() { super.onStart(); mapView?.onStart() }
    override fun onStop() { super.onStop(); mapView?.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView?.onLowMemory() }
    override fun onDestroyView() {
        super.onDestroyView()
        stopFindingOrder()
        mapView?.onDestroy()
        mapView = null
    }
}