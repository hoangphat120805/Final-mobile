package com.example.vaiche_driver.fragment

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.*
import android.location.Location
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
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.vaiche_driver.R
import com.example.vaiche_driver.model.DriverState
import com.example.vaiche_driver.navigation.CameraController
import com.example.vaiche_driver.navigation.MapboxNavManager
import com.example.vaiche_driver.navigation.RouteRenderer
import com.example.vaiche_driver.navigation.RouteRequester
import com.example.vaiche_driver.viewmodel.SharedViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.mapbox.bindgen.ExpectedFactory
import com.mapbox.geojson.Point
import com.mapbox.maps.ImageHolder
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.navigation.base.formatter.DistanceFormatterOptions
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.formatter.MapboxDistanceFormatter
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.tripdata.maneuver.api.MapboxManeuverApi
import com.mapbox.navigation.tripdata.maneuver.model.Maneuver
import com.mapbox.navigation.tripdata.maneuver.model.ManeuverError
import com.mapbox.navigation.tripdata.progress.api.MapboxTripProgressApi
import com.mapbox.navigation.tripdata.progress.model.DistanceRemainingFormatter
import com.mapbox.navigation.tripdata.progress.model.EstimatedTimeToArrivalFormatter
import com.mapbox.navigation.tripdata.progress.model.TimeRemainingFormatter
import com.mapbox.navigation.tripdata.progress.model.TripProgressUpdateFormatter
import com.mapbox.navigation.tripdata.speedlimit.api.MapboxSpeedInfoApi
import com.mapbox.navigation.ui.components.maneuver.view.MapboxManeuverView
import com.mapbox.navigation.ui.components.tripprogress.view.MapboxTripProgressView
import com.mapbox.navigation.ui.components.speedlimit.view.MapboxSpeedInfoView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DashboardFragment : Fragment() {

    private val vm: SharedViewModel by activityViewModels()

    // Views
    private var mapView: MapView? = null
    private var tvCenterTitle: TextView? = null
    private var maneuverView: MapboxManeuverView? = null
    private var tripProgressView: MapboxTripProgressView? = null
    private var speedLimitView: MapboxSpeedInfoView? = null

    // Map/Nav
    private var style: Style? = null
    private val navLocationProvider = com.mapbox.navigation.ui.maps.location.NavigationLocationProvider()
    private var lastAndroidLoc: Location? = null
    private var mapboxNavigation: MapboxNavigation? = null
    private var routeRenderer: RouteRenderer? = null
    private var cameraController: CameraController? = null

    // TripData
    private lateinit var distanceFormatter: DistanceFormatterOptions
    private lateinit var maneuverApi: MapboxManeuverApi
    private lateinit var tripProgressApi: MapboxTripProgressApi
    private lateinit var speedInfoApi: MapboxSpeedInfoApi

    // Fused
    private lateinit var fused: FusedLocationProviderClient

    // WS
    private val pingIntervalMs = 5_000L
    private var lastWsAt = 0L

    // Finding order loop
    private val findingHandler = Handler(Looper.getMainLooper())
    private var findingRunnable: Runnable? = null
    private var timeoutRunnable: Runnable? = null
    private var waitingFirstFix = false

    // Destination avatar annotation
    private var destManager: PointAnnotationManager? = null
    private var destAnnotation: PointAnnotation? = null
    private var lastDestPoint: Point? = null
    private var lastOwnerAvtUrl: String? = null

    // Collector avatar cache (to re-apply puck on state/style changes)
    private var lastCollectorAvtUrl: String? = null

    // Debounce follow camera to avoid zoom jitter when idle
    private var lastFollowAt = 0L
    private val followDebounceMs = 800L

    // Guard to avoid double-init when permission callback fires quickly
    private var initializedAfterPermission = false

    private val askFineLocation = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onLocationPermissionGranted()
        } else {
            Toast.makeText(requireContext(), "Location permission is required to display the map.", Toast.LENGTH_LONG).show()
        }
    }

    // ===== Observers =====
    private val locationObserver = object : LocationObserver {
        override fun onNewRawLocation(rawLocation: com.mapbox.common.location.Location) = Unit

        override fun onNewLocationMatcherResult(lmr: LocationMatcherResult) {
            val enhanced = lmr.enhancedLocation
            val keyPoints = lmr.keyPoints

            // 1) cập nhật puck
            navLocationProvider.changePosition(enhanced, keyPoints)

            // 2) cập nhật camera viewport (debounce để tránh giật zoom khi đứng yên)
            lastAndroidLoc = toAndroidLocation(enhanced)
            val now = System.currentTimeMillis()
            if ((enhanced.speed ?: 0.0) > 0.5 || now - lastFollowAt > followDebounceMs) {
                lastAndroidLoc?.let { cameraController?.onEnhancedLocationChanged(it) }
                cameraController?.requestFollowing()
                lastFollowAt = now
            }

            // 3) speed info
            speedInfoApi.updatePostedAndCurrentSpeed(lmr, distanceFormatter)?.let {
                speedLimitView?.render(it)
                speedLimitView?.visibility = View.VISIBLE
            }
            // 4) gửi ws khi đang giao
            maybeSendWs(enhanced.latitude, enhanced.longitude)
        }
    }

    private val routeProgressObserver = RouteProgressObserver { progress ->
        cameraController?.onRouteProgressChanged(progress)

        val manu = maneuverApi.getManeuvers(progress)
        maneuverView?.renderManeuvers(manu)
        tripProgressView?.render(tripProgressApi.getTripProgress(progress))

        maneuverView?.visibility = View.VISIBLE
        tripProgressView?.visibility = View.VISIBLE
        speedLimitView?.visibility = View.VISIBLE

        // vanishing update
        style?.let { s -> routeRenderer?.onRouteProgress(s, progress) }

        // đảm bảo puck ở trên trong lúc route cập nhật
        bringPuckToFront()

        cameraController?.requestFollowing()
    }

    private val routesObserver = RoutesObserver { res ->
        val s = style ?: return@RoutesObserver
        routeRenderer?.setRoutes(s, res.navigationRoutes)
        cameraController?.onRoutesChanged(res.navigationRoutes)

        // Tạo lại manager SAU khi vẽ route để layer marker ở trên
        destManager?.deleteAll()
        destManager = mapView?.annotations?.createPointAnnotationManager()

        // Re-apply marker nếu đã có
        val p = lastDestPoint
        val url = lastOwnerAvtUrl
        if (p != null) {
            viewLifecycleOwner.lifecycleScope.launch { setDestinationAvatar(p, url) }
        }

        // kéo puck lên trên (sau khi route thay đổi)
        bringPuckToFront()

        cameraController?.requestFollowing()
    }

    // ===== Warm avatar early (no logic change to image pipeline) =====
    private fun warmAvatarEarly() {
        viewLifecycleOwnerLiveData.observe(this) { owner ->
            owner?.lifecycleScope?.launch {
                val url: String? = withContext(Dispatchers.IO) {
                    try {
                        val repo = com.example.vaiche_driver.data.repository.ProfileRepository {
                            com.example.vaiche_driver.data.network.RetrofitClient.instance
                        }
                        repo.getUserProfile().getOrNull()?.avatarUrl
                    } catch (_: Throwable) { null }
                }
                if (url != null) {
                    lastCollectorAvtUrl = url
                    withContext(Dispatchers.IO) {
                        try {
                            Glide.with(requireContext())
                                .asBitmap()
                                .load(url)
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .preload(128, 128)
                        } catch (_: Throwable) { }
                    }
                }
            }
        }
    }

    // ===== Lifecycle =====
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapboxNavManager.setup(requireContext().applicationContext)
        warmAvatarEarly()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_dashboard, container, false)

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        MapboxNavManager.attach(viewLifecycleOwner)
        mapboxNavigation = MapboxNavManager.current()

        mapView = view.findViewById(R.id.mapView)
        tvCenterTitle = view.findViewById(R.id.tvCenterTitle)
        maneuverView = view.findViewById(R.id.maneuverView)
        tripProgressView = view.findViewById(R.id.tripProgressView)
        speedLimitView = view.findViewById(R.id.speedLimitView)

        fused = LocationServices.getFusedLocationProviderClient(requireContext())

        routeRenderer = RouteRenderer(requireContext())
        cameraController = CameraController(mapView!!)

        // ==== LẮNG NGHE tín hiệu từ Settings/EditProfile để reload avatar puck ====
        parentFragmentManager.setFragmentResultListener("profile_updated", viewLifecycleOwner) { _, b ->
            if (b.getBoolean("changed", false)) {
                viewLifecycleOwner.lifecycleScope.launch { refreshCollectorAvatar() }
            }
        }
        // Key dự phòng nếu bạn muốn gửi URL trực tiếp
        parentFragmentManager.setFragmentResultListener("collector_avatar_updated", viewLifecycleOwner) { _, b ->
            val newUrl = b.getString("url")
            viewLifecycleOwner.lifecycleScope.launch { setCollectorPuckFromUrl(newUrl) }
        }

        // Load style và gắn provider SAU khi xong style
        mapView?.mapboxMap?.loadStyleUri("mapbox://styles/mapbox/navigation-day-v1") { st ->
            style = st
            mapView?.location?.setLocationProvider(navLocationProvider)
            mapView?.location?.updateSettings {
                enabled = true
                pulsingEnabled = true
                puckBearingEnabled = true
                puckBearing = PuckBearing.COURSE
                locationPuck = LocationPuck2D()
            }

            // ✅ Hiển thị placeholder ngay lập tức, không chờ ảnh mạng
            showDefaultPuck()

            // Sau khi style load xong: đặt avatar puck (cache-first, không đổi logic cũ)
            lifecycleScope.launch {
                if (lastCollectorAvtUrl == null) {
                    refreshCollectorAvatar() // fetch url + set puck
                } else {
                    setCollectorPuckFromUrl(lastCollectorAvtUrl) // re-apply cache
                }
            }

            // đảm bảo puck luôn bật & re-apply avatar nếu có
            ensurePuckVisible()
        }

        // TripData init
        distanceFormatter = DistanceFormatterOptions.Builder(requireContext()).build()
        maneuverApi = MapboxManeuverApi(MapboxDistanceFormatter(distanceFormatter))
        val tpFormatter = TripProgressUpdateFormatter.Builder(requireContext())
            .estimatedTimeToArrivalFormatter(EstimatedTimeToArrivalFormatter(requireContext()))
            .timeRemainingFormatter(TimeRemainingFormatter(requireContext(), null))
            .distanceRemainingFormatter(DistanceRemainingFormatter(distanceFormatter))
            .build()
        tripProgressApi = MapboxTripProgressApi(tpFormatter)
        speedInfoApi = MapboxSpeedInfoApi()

        tvCenterTitle?.setOnClickListener { vm.toggleOnlineStatus() }

        bindViewModel()
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    override fun onStart() {
        super.onStart()
        mapView?.onStart()

        if (!hasLocationPermission()) {
            askFineLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            onLocationPermissionGranted()
        }
    }

    override fun onStop() {
        super.onStop()
        mapView?.onStop()
        stopFindingOrder()
        stopTripSession()
        initializedAfterPermission = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopFindingOrder()
        stopTripSession()
        routeRenderer?.cancel()
        maneuverApi.cancel()
        destManager?.deleteAll()
        destManager = null
        destAnnotation = null
        mapView?.onDestroy()
        mapView = null
    }

    // ===== VM wiring =====
    private fun bindViewModel() {
        vm.driverState.observe(viewLifecycleOwner) { state ->
            stopFindingOrder()
            when (state) {
                DriverState.OFFLINE -> {
                    tvCenterTitle?.text = "Offline"; tvCenterTitle?.isEnabled = true
                    clearRoutes()
                    clearDestinationAvatar()
                    mapboxNavigation?.setNavigationRoutes(emptyList())
                    vm.stopWebSocket()
                    startTripSession()
                    ensurePuckVisible()
                    cameraController?.requestFollowing()
                }
                DriverState.ONLINE -> {
                    tvCenterTitle?.text = "Online"; tvCenterTitle?.isEnabled = true
                    clearRoutes()
                    clearDestinationAvatar()
                    vm.stopWebSocket()
                    if (parentFragmentManager.findFragmentByTag("SetPlanDialog") == null) {
                        SetPlanDialogFragment().show(parentFragmentManager, "SetPlanDialog")
                    }
                    startTripSession()
                    ensurePuckVisible()
                    cameraController?.requestFollowing()
                }
                DriverState.FINDING_ORDER -> {
                    tvCenterTitle?.text = "Finding..."; tvCenterTitle?.isEnabled = false
                    clearRoutes()
                    clearDestinationAvatar()
                    vm.stopWebSocket()
                    startTripSession()
                    ensurePuckVisible()
                    startFindingOrder(immediate = true)
                    cameraController?.requestFollowing()
                }
                DriverState.DELIVERING -> {
                    tvCenterTitle?.text = "Delivering"; tvCenterTitle?.isEnabled = false
                    startTripSession()
                    ensurePuckVisible()
                    requestRouteForActiveOrder()
                    cameraController?.requestFollowing()

                    // Tải avatar collector + owner và gán UI (giữ logic cũ, nhưng kèm placeholder đã hiện ngay)
                    val orderId = vm.activeOrder.value?.id
                    lifecycleScope.launch {
                        // 1) avatar collector (chính mình) — refresh để lấy bản mới nhất khi vào DELIVERING
                        refreshCollectorAvatar()

                        // 2) avatar user (owner) + set vào điểm đến (sau khi có routePoints)
                        val ownerAvt: String? = try {
                            if (orderId != null) {
                                val repo = com.example.vaiche_driver.data.repository.OrderRepository {
                                    com.example.vaiche_driver.data.network.RetrofitClient.instance
                                }
                                repo.getOrderOwner(orderId).getOrNull()?.avatarUrl
                            } else null
                        } catch (_: Throwable) { null }

                        vm.routePoints.value?.lastOrNull()?.let { dest ->
                            setDestinationAvatar(dest, ownerAvt)
                        }
                    }
                }
            }
        }

        vm.foundNewOrder.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { newOrder ->
                stopFindingOrder()
                (parentFragmentManager.findFragmentByTag(SuccessDialogFragment.TAG) as? DialogFragment)?.dismiss()
                NewOrderDialogFragment.newInstanceWithSeed(newOrder)
                    .show(parentFragmentManager, "NewOrderDialog")
            }
        }

        vm.routePoints.observe(viewLifecycleOwner) { pts ->
            if (pts.isNullOrEmpty()) { clearRoutes(); return@observe }

            val origin = navLocationProvider.lastLocation?.let {
                Point.fromLngLat(it.longitude, it.latitude)
            } ?: pts.first()
            val dest = pts.last()
            val bearing: Double? = navLocationProvider.lastLocation?.bearing?.toDouble()

            mapboxNavigation?.let { nav ->
                RouteRequester.request(
                    context = requireContext(),
                    mapboxNavigation = nav,
                    origin = origin,
                    destination = dest,
                    bearingDeg = bearing,
                    allowAlternatives = true
                ) { /* RoutesObserver sẽ vẽ route */ }
            }

            // Cập nhật marker đích theo avatar owner
            lifecycleScope.launch {
                val orderId = vm.activeOrder.value?.id
                val ownerAvt: String? = try {
                    if (orderId != null) {
                        val repo = com.example.vaiche_driver.data.repository.OrderRepository {
                            com.example.vaiche_driver.data.network.RetrofitClient.instance
                        }
                        repo.getOrderOwner(orderId).getOrNull()?.avatarUrl
                    } else null
                } catch (_: Throwable) { null }
                setDestinationAvatar(pts.last(), ownerAvt)
            }
        }

        vm.errorMessage.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { msg ->
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    // ===== Route BE -> xin Mapbox route =====
    private fun requestRouteForActiveOrder() {
        val order = vm.activeOrder.value ?: return

        fun callBe(lat: Double, lng: Double) {
            vm.loadRoute(order.id, lat, lng)
        }

        navLocationProvider.lastLocation?.let { callBe(it.latitude, it.longitude); return }

        if (hasLocationPermission()) {
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    if (loc != null) callBe(loc.latitude, loc.longitude) else waitForFirstEnhancedFix(::callBe)
                }
                .addOnFailureListener { waitForFirstEnhancedFix(::callBe) }
        } else {
            askFineLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            waitForFirstEnhancedFix(::callBe)
        }
    }

    private fun waitForFirstEnhancedFix(onFix: (Double, Double) -> Unit) {
        if (waitingFirstFix) return
        waitingFirstFix = true

        val tmp = object : LocationObserver {
            override fun onNewRawLocation(rawLocation: com.mapbox.common.location.Location) = Unit
            override fun onNewLocationMatcherResult(res: LocationMatcherResult) {
                val e = res.enhancedLocation
                onFix(e.latitude, e.longitude)
                mapboxNavigation?.unregisterLocationObserver(this)
                waitingFirstFix = false
            }
        }
        mapboxNavigation?.registerLocationObserver(tmp)

        Handler(Looper.getMainLooper()).postDelayed({
            if (waitingFirstFix) {
                mapboxNavigation?.unregisterLocationObserver(tmp)
                waitingFirstFix = false
                Toast.makeText(requireContext(), "Unable to get GPS location.", Toast.LENGTH_LONG).show()
            }
        }, 10_000)
    }

    // ===== Trip session =====
    @SuppressLint("MissingPermission")
    private fun startTripSession() {
        val nav = mapboxNavigation ?: MapboxNavManager.current() ?: return
        mapboxNavigation = nav

        nav.unregisterLocationObserver(locationObserver)
        nav.unregisterRouteProgressObserver(routeProgressObserver)
        nav.unregisterRoutesObserver(routesObserver)

        nav.registerLocationObserver(locationObserver)
        nav.registerRouteProgressObserver(routeProgressObserver)
        nav.registerRoutesObserver(routesObserver)

        nav.startTripSession()
    }

    private fun stopTripSession() {
        val nav = mapboxNavigation ?: return
        kotlin.runCatching {
            nav.unregisterRouteProgressObserver(routeProgressObserver)
            nav.unregisterLocationObserver(locationObserver)
            nav.unregisterRoutesObserver(routesObserver)
            nav.stopTripSession()
        }
        mapboxNavigation = null
    }

    // ===== Permission → full init entrypoint =====
    @SuppressLint("MissingPermission")
    private fun onLocationPermissionGranted() {
        if (!isAdded || view == null || mapView == null) return
        if (initializedAfterPermission) return
        initializedAfterPermission = true

        if (style == null) {
            mapView?.mapboxMap?.loadStyleUri("mapbox://styles/mapbox/navigation-day-v1") { st ->
                style = st
                mapView?.location?.setLocationProvider(navLocationProvider)
                ensurePuckVisible()
                startTripSession()
                kickstartFlowsByState()
            }
        } else {
            ensurePuckVisible()
            startTripSession()
            kickstartFlowsByState()
        }
    }

    private fun kickstartFlowsByState() {
        when (vm.driverState.value) {
            DriverState.OFFLINE -> {
                cameraController?.requestFollowing()
            }
            DriverState.ONLINE -> {
                cameraController?.requestFollowing()
            }
            DriverState.FINDING_ORDER -> {
                cameraController?.requestFollowing()
                startFindingOrder(immediate = true)
            }
            DriverState.DELIVERING -> {
                cameraController?.requestFollowing()
                requestRouteForActiveOrder()
                viewLifecycleOwner.lifecycleScope.launch { refreshCollectorAvatar() }
            }
            else -> Unit
        }
    }

    // ===== Helpers =====
    private fun enablePuck() {
        mapView?.location?.setLocationProvider(navLocationProvider)
        mapView?.location?.updateSettings { enabled = true }
    }

    /** đảm bảo LocationComponent bật và re-apply avatar nếu đã có */
    private fun ensurePuckVisible() {
        mapView?.location?.setLocationProvider(navLocationProvider)
        mapView?.location?.updateSettings {
            enabled = true
            pulsingEnabled = true
            puckBearingEnabled = true
            puckBearing = PuckBearing.COURSE
        }
        // nếu có avatar đã cache → re-apply; nếu chưa có → lấy URL ngay
        if (lastCollectorAvtUrl != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                try { setCollectorPuckFromUrl(lastCollectorAvtUrl) } catch (_: Throwable) {}
            }
        } else {
            viewLifecycleOwner.lifecycleScope.launch {
                try { refreshCollectorAvatar() } catch (_: Throwable) {}
            }
        }
        bringPuckToFront()
    }

    private fun clearRoutes() {
        style?.let { s -> routeRenderer?.clear(s) }

        val emptyManeuvers = ExpectedFactory.createValue<ManeuverError, List<Maneuver>>(emptyList())
        maneuverView?.renderManeuvers(emptyManeuvers)

        maneuverView?.visibility = View.GONE
        tripProgressView?.visibility = View.GONE
        speedLimitView?.visibility = View.GONE
        // KHÔNG động vào puck
    }

    private fun clearDestinationAvatar() {
        destAnnotation?.let { destManager?.delete(it) }
        destAnnotation = null
        lastDestPoint = null
        lastOwnerAvtUrl = null
    }

    private fun toAndroidLocation(src: com.mapbox.common.location.Location): Location =
        Location("mapbox").apply {
            latitude = src.latitude
            longitude = src.longitude
            altitude = (src.altitude ?: 0.0)
            bearing = (src.bearing ?: 0.0).toFloat()
            speed = (src.speed ?: 0.0).toFloat()
        }

    private fun maybeSendWs(lat: Double, lng: Double) {
        if (vm.driverState.value != DriverState.DELIVERING) return
        val now = System.currentTimeMillis()
        if (now - lastWsAt >= pingIntervalMs) {
            vm.sendLocation(lat, lng)
            lastWsAt = now
        }
    }

    // ===== Finding order =====
    private fun startFindingOrder(immediate: Boolean = false) {
        stopFindingOrder()
        findingRunnable = Runnable {
            if (!isAdded) return@Runnable
            vm.findNearbyOrder()
            findingHandler.postDelayed(findingRunnable!!, 5_000L)
        }
        findingHandler.postDelayed(findingRunnable!!, if (immediate) 0L else 3_000L)
        timeoutRunnable = Runnable {
            if (!isAdded) return@Runnable
            if (vm.driverState.value == DriverState.FINDING_ORDER) {
                stopFindingOrder()
                Toast.makeText(requireContext(), "No orders found.", Toast.LENGTH_LONG).show()
                vm.goOffline()
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

    // ===== Avatars: puck (collector) & destination marker (owner) =====

    /** Hiển thị puck mặc định (placeholder) ngay lập tức */
    private fun showDefaultPuck() {
        val ph = BitmapFactory.decodeResource(resources, R.drawable.ic_user)
        mapView?.location?.updateSettings {
            enabled = true
            pulsingEnabled = true
            puckBearingEnabled = true
            puckBearing = PuckBearing.COURSE
            locationPuck = LocationPuck2D(
                bearingImage = ImageHolder.from(ph),
                topImage = ImageHolder.from(ph),
                shadowImage = null
            )
        }
        bringPuckToFront()
    }

    /** Gọi API profile lấy avatarUrl và set puck (giữ logic cũ, bọc tiện hơn) */
    private suspend fun refreshCollectorAvatar() {
        val myAvt: String? = withContext(Dispatchers.IO) {
            try {
                val repo = com.example.vaiche_driver.data.repository.ProfileRepository {
                    com.example.vaiche_driver.data.network.RetrofitClient.instance
                }
                repo.getUserProfile().getOrNull()?.avatarUrl
            } catch (_: Throwable) { null }
        }
        setCollectorPuckFromUrl(myAvt)
    }

    private suspend fun loadCircleBitmap(
        url: String?,
        sizePx: Int = 112,
        borderPx: Int = 6
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (url.isNullOrBlank()) return@withContext null
        // 1) cache-first: lấy ngay nếu đã có (nhanh, tránh mạng)
        try {
            val cached = Glide.with(requireContext())
                .asBitmap()
                .load(url)
                .onlyRetrieveFromCache(true)
                .submit(sizePx, sizePx)
                .get()
            return@withContext circleCropWithBorder(cached, sizePx, borderPx)
        } catch (_: Throwable) {
            // 2) cache miss → tải MẠNG NGAY (blocking trên IO) để đảm bảo lần đầu có ảnh
            return@withContext try {
                val fresh = Glide.with(requireContext())
                    .asBitmap()
                    .load(url)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .submit(sizePx, sizePx)
                    .get()
                circleCropWithBorder(fresh, sizePx, borderPx)
            } catch (_: Throwable) {
                null
            }
        }
    }


    private fun circleCropWithBorder(bmp: Bitmap, sizePx: Int, borderPx: Int): Bitmap {
        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val r = sizePx / 2f
        val path = Path().apply { addCircle(r, r, r - borderPx, Path.Direction.CW) }
        c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        c.save()
        c.clipPath(path)
        c.drawBitmap(bmp, Rect(0,0,bmp.width,bmp.height), Rect(0,0,sizePx,sizePx), null)
        c.restore()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = borderPx.toFloat()
        }
        c.drawCircle(r, r, r - borderPx, paint)
        return out
    }

    private suspend fun setCollectorPuckFromUrl(url: String?) {
        lastCollectorAvtUrl = url // cache để re-apply
        val bmp = loadCircleBitmap(url, sizePx = 128, borderPx = 6) ?: return

        withContext(Dispatchers.Main) {
            mapView?.location?.updateSettings {
                enabled = true
                pulsingEnabled = true
                puckBearingEnabled = true
                puckBearing = PuckBearing.COURSE
                locationPuck = LocationPuck2D(
                    bearingImage = ImageHolder.from(bmp),
                    topImage = ImageHolder.from(bmp),
                    shadowImage = null
                )
            }
            bringPuckToFront()
        }
    }

    private suspend fun setDestinationAvatar(point: Point, url: String?) {
        // Lưu lại để có thể re-apply khi route thay đổi (layer order)
        lastDestPoint = point
        lastOwnerAvtUrl = url

        val bmp = loadCircleBitmap(url, sizePx = 128, borderPx = 6) ?: return
        withContext(Dispatchers.Main) {
            if (destManager == null) {
                destManager = mapView?.annotations?.createPointAnnotationManager()
            }
            val manager = destManager ?: return@withContext
            val opts = PointAnnotationOptions()
                .withPoint(point)
                .withIconImage(bmp)

            destAnnotation?.let { manager.delete(it) }
            destAnnotation = manager.create(opts)
        }
    }

    // ===== Kéo Puck (location layers) lên TOP =====
    private fun bringPuckToFront() {
        val st = style ?: return
        val puckLayers = listOf(
            "mapbox-location-layer",
            "mapbox-location-shadow-layer",
            "mapbox-location-accuracy-layer",
            "mapbox-location-accuracy-circle-layer",
            "mapbox-location-indicator-layer",
            "mapbox-location-puck-layer",
            "mapbox-location-top-layer"
        )
        puckLayers.forEach { id ->
            runCatching { st.moveStyleLayer(id, null) } // null = move to top
        }
    }

}

