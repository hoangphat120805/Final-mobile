package com.example.vaiche_driver.fragment

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
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
import com.example.vaiche_driver.model.DriverState
import com.example.vaiche_driver.viewmodel.SharedViewModel
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.locationcomponent.location

/**
 * Màn hình Dashboard: lắng nghe trạng thái từ SharedViewModel và điều phối UI/luồng tìm đơn.
 */
class DashboardFragment : Fragment() {

    private val TAG = "Dashboard"

    private val sharedViewModel: SharedViewModel by activityViewModels()

    private var mapView: MapView? = null

    private val findingHandler = Handler(Looper.getMainLooper())
    private var findingRunnable: Runnable? = null
    private var timeoutRunnable: Runnable? = null

    // Xin quyền vị trí
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                initLocationComponent()
            } else {
                Toast.makeText(
                    context,
                    "Location permission is required for the map feature.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_dashboard, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapView = view.findViewById(R.id.mapView)

        // Khởi tạo map
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
    }

    private fun observeViewModel() {
        val tvCenterTitle = view?.findViewById<TextView>(R.id.tvCenterTitle) ?: return

        sharedViewModel.driverState.observe(viewLifecycleOwner) { state ->
            // Mỗi lần đổi state thì dừng polling cũ để tránh nhân đôi
            stopFindingOrder()

            when (state) {
                DriverState.OFFLINE -> {
                    tvCenterTitle.text = "Offline"
                    tvCenterTitle.isEnabled = true
                }
                DriverState.ONLINE -> {
                    tvCenterTitle.text = "Online"
                    tvCenterTitle.isEnabled = true
                    if (parentFragmentManager.findFragmentByTag("SetPlanDialog") == null) {
                        SetPlanDialogFragment().show(parentFragmentManager, "SetPlanDialog")
                    }
                }
                DriverState.FINDING_ORDER -> {
                    tvCenterTitle.text = "Finding..."
                    tvCenterTitle.isEnabled = false
                    // Bắt đầu tìm + set timeout 30s
                    startFindingOrder(immediate = true)
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
                // Reset vòng tìm + reset lại timeout 30s
                startFindingOrder(immediate = true)
            }
        }

        sharedViewModel.foundNewOrder.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { newOrder ->
                // Có đơn mới -> dừng polling + đóng "đang chờ"
                stopFindingOrder()
                (parentFragmentManager.findFragmentByTag(SuccessDialogFragment.TAG) as? DialogFragment)?.dismiss()
                NewOrderDialogFragment.newInstanceWithSeed(newOrder)
                    .show(parentFragmentManager, "NewOrderDialog")
                // TODO: Vẽ marker/route cho newOrder trên map
            }
        }
    }

    private fun startFindingOrder(immediate: Boolean = false) {
        // Luôn dọn dẹp trước khi start để chắc chắn reset lại timeout/polling
        Log.d(TAG, "startFindingOrder(immediate=$immediate)")
        stopFindingOrder()

        // Poll tìm đơn mỗi 5s
        findingRunnable = Runnable {
            if (!isAdded) return@Runnable
            sharedViewModel.findNearbyOrder()
            findingHandler.postDelayed(findingRunnable!!, 5_000L)
        }

        val initialDelay = if (immediate) 0L else 3_000L
        findingHandler.postDelayed(findingRunnable!!, initialDelay)

        // Timeout 30s: nếu vẫn ở FINDING_ORDER thì tự về OFFLINE
        timeoutRunnable = Runnable {
            if (!isAdded) return@Runnable
            if (sharedViewModel.driverState.value == DriverState.FINDING_ORDER) {
                stopFindingOrder()
                Toast.makeText(
                    requireContext(),
                    "Can not find any orders!",
                    Toast.LENGTH_LONG
                ).show()
                sharedViewModel.goOffline()
            }
        }
        findingHandler.postDelayed(timeoutRunnable!!, 30_000L)
        Log.d(TAG, "poll -> findNearbyOrder()")
    }

    private fun stopFindingOrder() {
        findingRunnable?.let { findingHandler.removeCallbacks(it) }
        timeoutRunnable?.let { findingHandler.removeCallbacks(it) }
        findingRunnable = null
        timeoutRunnable = null
    }

    // --- Mapbox helpers ---
    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            initLocationComponent()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun initLocationComponent() {
        val locationComponentPlugin = mapView?.location
        locationComponentPlugin?.updateSettings {
            this.enabled = true
            // Có thể thêm tuỳ chỉnh biểu tượng vị trí tại đây
        }
    }

    // --- Lifecycle MapView ---
    override fun onStart() { super.onStart(); mapView?.onStart() }
    override fun onStop() { super.onStop(); mapView?.onStop(); stopFindingOrder() }
    override fun onLowMemory() { super.onLowMemory(); mapView?.onLowMemory() }
    override fun onDestroyView() {
        super.onDestroyView()
        stopFindingOrder()
        mapView?.onDestroy()
        mapView = null
    }
}
