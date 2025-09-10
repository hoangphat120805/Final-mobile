package com.example.vaiche_driver.fragment

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager
import com.mapbox.maps.plugin.locationcomponent.location

/**
 * Dashboard:
 * - Điều khiển Online/Offline, tìm đơn
 * - Khi Delivering: lấy route từ backend, vẽ polyline
 * - Sau khi có route: mở WebSocket và gửi vị trí mỗi 5s + map auto follow collector
 */
class DashboardFragment : Fragment() {

    private val sharedViewModel: SharedViewModel by activityViewModels()

    private var mapView: MapView? = null
    private var polylineManager: PolylineAnnotationManager? = null
    private lateinit var fused: FusedLocationProviderClient

    private val findingHandler = Handler(Looper.getMainLooper())
    private var findingRunnable: Runnable? = null
    private var timeoutRunnable: Runnable? = null

    // Ping vị trí định kỳ lên WebSocket
    private val pingHandler = Handler(Looper.getMainLooper())
    private var pingRunnable: Runnable? = null
    private var pingIntervalMs: Long = 5_000 // đổi 10_000 nếu bạn muốn 10s

    private var socketStarted = false

    // Xin quyền vị trí
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) initLocationComponent()
            else Toast.makeText(
                context,
                "Cần quyền vị trí để hiển thị bản đồ.",
                Toast.LENGTH_LONG
            ).show()
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_dashboard, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapView = view.findViewById(R.id.mapView)
        fused = LocationServices.getFusedLocationProviderClient(requireContext())

        mapView?.mapboxMap?.loadStyle(Style.MAPBOX_STREETS) {
            checkLocationPermission()
            ensurePolylineManager()
        }


        setupClickListeners(view)
        observeViewModel()
    }

    private fun setupClickListeners(view: View) {
        val tvCenterTitle = view.findViewById<TextView>(R.id.tvCenterTitle)
        tvCenterTitle.setOnClickListener { sharedViewModel.toggleOnlineStatus() }
    }

    private fun observeViewModel() {
        val tvCenterTitle = view?.findViewById<TextView>(R.id.tvCenterTitle) ?: return

        sharedViewModel.driverState.observe(viewLifecycleOwner) { state ->
            stopFindingOrder()
            stopLocationPinger()
            socketStarted = false

            when (state) {
                DriverState.OFFLINE -> {
                    tvCenterTitle.text = "Offline"
                    tvCenterTitle.isEnabled = true
                    clearRoute()
                    sharedViewModel.stopWebSocket()
                }
                DriverState.ONLINE -> {
                    tvCenterTitle.text = "Online"
                    tvCenterTitle.isEnabled = true
                    clearRoute()
                    sharedViewModel.stopWebSocket()
                    if (parentFragmentManager.findFragmentByTag("SetPlanDialog") == null) {
                        SetPlanDialogFragment().show(parentFragmentManager, "SetPlanDialog")
                    }
                }
                DriverState.FINDING_ORDER -> {
                    tvCenterTitle.text = "Finding..."
                    tvCenterTitle.isEnabled = false
                    clearRoute()
                    sharedViewModel.stopWebSocket()
                    startFindingOrder(immediate = true)
                }
                DriverState.DELIVERING -> {
                    tvCenterTitle.text = "Delivering"
                    tvCenterTitle.isEnabled = false
                    sharedViewModel.activeOrder.value?.let { schedule ->
                        getLastLocation(
                            onGot = { lat, lng ->
                                sharedViewModel.loadRoute(schedule.id, lat, lng)
                                focusOnCollector(lat, lng)
                            },
                            onFail = {
                                Toast.makeText(requireContext(), "Không lấy được vị trí để tính lộ trình", Toast.LENGTH_LONG).show()
                                clearRoute()
                            }
                        )
                    } ?: clearRoute()
                }
            }
        }

        sharedViewModel.foundNewOrder.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { newOrder ->
                stopFindingOrder()
                (parentFragmentManager.findFragmentByTag(SuccessDialogFragment.TAG) as? DialogFragment)?.dismiss()
                NewOrderDialogFragment.newInstanceWithSeed(newOrder)
                    .show(parentFragmentManager, "NewOrderDialog")
            }
        }

        sharedViewModel.routePoints.observe(viewLifecycleOwner) { pts ->
            if (pts.isNullOrEmpty()) {
                clearRoute()
            } else {
                renderRoute(pts)
                fitCameraTo(pts)

                val order = sharedViewModel.activeOrder.value ?: return@observe
                if (!socketStarted) {
                    val token = com.example.vaiche_driver.data.local.SessionManager(requireContext()).fetchAuthToken()
                    if (token.isNullOrBlank()) {
                        Toast.makeText(requireContext(), "Thiếu access token để mở WebSocket", Toast.LENGTH_LONG).show()
                        return@observe
                    }
                    sharedViewModel.openWebSocket(order.id, token)
                    startLocationPinger()
                    socketStarted = true
                }
            }
        }

        sharedViewModel.errorMessage.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
            }
        }
    }

    // =============== Polling tìm đơn ===============
    private fun startFindingOrder(immediate: Boolean = false) {
        stopFindingOrder()
        findingRunnable = Runnable {
            if (!isAdded) return@Runnable
            sharedViewModel.findNearbyOrder()
            findingHandler.postDelayed(findingRunnable!!, 5_000L)
        }
        val initialDelay = if (immediate) 0L else 3_000L
        findingHandler.postDelayed(findingRunnable!!, initialDelay)

        timeoutRunnable = Runnable {
            if (!isAdded) return@Runnable
            if (sharedViewModel.driverState.value == DriverState.FINDING_ORDER) {
                stopFindingOrder()
                Toast.makeText(requireContext(), "Không tìm được đơn nào.", Toast.LENGTH_LONG).show()
                sharedViewModel.goOffline()
            }
        }
        findingHandler.postDelayed(timeoutRunnable!!, 30_000L)
    }

    private fun stopFindingOrder() {
        findingRunnable?.let { findingHandler.removeCallbacks(it) }
        timeoutRunnable?.let { findingHandler.removeCallbacks(it) }
        findingRunnable = null
        timeoutRunnable = null
    }

    // ====================== Mapbox helpers ======================
    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            initLocationComponent()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun initLocationComponent() {
        mapView?.location?.updateSettings { enabled = true }
    }

    private fun ensurePolylineManager() {
        if (polylineManager == null && mapView != null) {
            polylineManager = mapView!!.annotations.createPolylineAnnotationManager()
        }
    }

    private fun clearRoute() {
        polylineManager?.deleteAll()
    }

    private fun renderRoute(points: List<Point>) {
        ensurePolylineManager()
        polylineManager?.deleteAll()
        val options = PolylineAnnotationOptions()
            .withPoints(points)
            .withLineWidth(5.0)
            .withLineColor("#2E86DE")
        polylineManager?.create(options)
    }

    private fun fitCameraTo(points: List<Point>) {
        if (points.isEmpty()) return
        val map = mapView?.mapboxMap ?: return
        val padding = EdgeInsets(100.0, 80.0, 140.0, 80.0)
        val cam = map.cameraForCoordinates(points, padding, bearing = null, pitch = null)
        map.setCamera(cam)
    }

    // ====================== WebSocket ping helpers ======================
    private fun startLocationPinger() {
        stopLocationPinger()
        pingRunnable = object : Runnable {
            override fun run() {
                if (!isAdded) return
                if (ContextCompat.checkSelfPermission(
                        requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    scheduleNextPing()
                    return
                }

                fused.lastLocation
                    .addOnSuccessListener { loc ->
                        if (loc != null) {
                            sharedViewModel.sendLocation(loc.latitude, loc.longitude)
                            focusOnCollector(loc.latitude, loc.longitude) // 👈 luôn auto follow
                        }
                        scheduleNextPing()
                    }
                    .addOnFailureListener {
                        scheduleNextPing()
                    }
            }
        }
        pingHandler.post(pingRunnable!!)
    }

    private fun scheduleNextPing() {
        pingRunnable?.let { pingHandler.postDelayed(it, pingIntervalMs) }
    }

    private fun stopLocationPinger() {
        pingRunnable?.let { pingHandler.removeCallbacks(it) }
        pingRunnable = null
    }

    // --- Lifecycle MapView ---
    override fun onStart() { super.onStart(); mapView?.onStart() }
    override fun onStop() {
        super.onStop()
        mapView?.onStop()
        stopFindingOrder()
        stopLocationPinger()
    }
    override fun onLowMemory() { super.onLowMemory(); mapView?.onLowMemory() }
    override fun onDestroyView() {
        super.onDestroyView()
        stopFindingOrder()
        stopLocationPinger()
        polylineManager?.deleteAll()
        polylineManager = null
        mapView?.onDestroy()
        mapView = null
    }

    private fun getLastLocation(
        onGot: (Double, Double) -> Unit,
        onFail: () -> Unit
    ) {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            onFail()
            return
        }

        fused.lastLocation
            .addOnSuccessListener { loc ->
                if (loc != null) onGot(loc.latitude, loc.longitude) else onFail()
            }
            .addOnFailureListener { onFail() }
    }

    private fun focusOnCollector(lat: Double, lng: Double) {
        val map = mapView?.mapboxMap ?: return
        val cam = CameraOptions.Builder()
            .center(Point.fromLngLat(lng, lat))
            .zoom(20.0) // zoom cao hơn để rõ đường
            .build()
        map.setCamera(cam)
    }
}
