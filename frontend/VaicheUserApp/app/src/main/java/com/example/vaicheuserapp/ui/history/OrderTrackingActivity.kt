package com.example.vaicheuserapp.ui.history

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import com.example.vaicheuserapp.R
import com.example.vaicheuserapp.data.model.CollectorPublic
import com.example.vaicheuserapp.data.model.OrderPublic
import com.example.vaicheuserapp.data.model.UserPublic
import com.example.vaicheuserapp.data.network.RetrofitClient
import com.example.vaicheuserapp.databinding.ActivityOrderTrackingBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.ImageHolder
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.locationcomponent.location
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfMeasurement
import android.R.attr.strokeWidth
import android.graphics.Paint

class OrderTrackingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOrderTrackingBinding
    private lateinit var mapView: MapView
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var order: OrderPublic? = null
    private var owner: UserPublic? = null
    private var collector: CollectorPublic? = null

    private var ownerPickupLocation: Point? = null
    private var collectorCurrentLocation: Point? = null

    private var pointAnnotationManager: PointAnnotationManager? = null
    private var ownerMarker: PointAnnotation? = null
    private var collectorMarker: PointAnnotation? = null

    // WebSocket
    private var webSocket: WebSocket? = null
    private val okHttpClient = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()
    private val gson = Gson()

    // Permissions launcher
    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            enableLocationComponent()
            getLastKnownLocation(false) // Just log location, don't move camera
        } else {
            Toast.makeText(this, "Location permissions are required for tracking.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrderTrackingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mapView = binding.mapView

        setupToolbar()
        setupMapboxMap()
        setupListeners()

        order = getOrderFromIntent()
        order?.let { currentOrder ->
            // Check for valid location coordinates
            if (currentOrder.locationGeoJson.coordinates.size >= 2) {
                val longitude = currentOrder.locationGeoJson.coordinates[0]
                val latitude = currentOrder.locationGeoJson.coordinates[1]
                ownerPickupLocation = Point.fromLngLat(longitude, latitude)
                Log.d("OrderTracking", "Owner pickup location from GeoJSON: $latitude, $longitude")
            } else {
                Log.e("OrderTracking", "Order GeoJSON location has insufficient coordinates for order ID: ${currentOrder.id}")
                Toast.makeText(this, "Order location is missing or invalid in GeoJSON.", Toast.LENGTH_LONG).show()
                finish()
                return
            }

            ownerPickupLocation?.let { addOrUpdateMarker(it, isCollector = false) }

            owner = currentOrder.owner
            collector = currentOrder.collector

            fetchCollectorDetails(currentOrder.id)
            connectWebSocket(currentOrder.id)

        } ?: run {
            Toast.makeText(this, "Order not found for tracking.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun updateOwnerUI(owner: UserPublic) {
        // Not directly displayed on tracking screen, but useful for logs or future UI.
        Log.d("OrderTracking", "Order Owner: ${owner.fullName}, Phone: ${owner.phoneNumber}")
    }

    private fun getOrderFromIntent(): OrderPublic? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("EXTRA_ORDER", OrderPublic::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("EXTRA_ORDER")
        }
    }

    private fun setupToolbar() {
        binding.ivBackButton.setOnClickListener { finish() }
    }

    private fun setupListeners() {
        binding.btnContactCollector.setOnClickListener {
            Toast.makeText(this, "Contact collector clicked", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupMapboxMap() {
        mapView.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS) { style ->
            try {
                pointAnnotationManager = mapView.annotations.createPointAnnotationManager()
            } catch (e: Exception) {
                Log.e("OrderTracking", "Failed to create PointAnnotationManager: ${e.message}")
                pointAnnotationManager = null
            }

            // Add owner marker here, after manager is initialized and map is ready
            // This ensures owner's initial pickup location is marked.
            ownerPickupLocation?.let { ownerPoint ->
                addOrUpdateMarker(ownerPoint, isCollector = false)
                // --- NEW: Post camera move to ensure map is fully laid out ---
                mapView.post {
                    moveCameraToPoints(ownerPoint, collectorCurrentLocation)
                }
            }

            // --- CRITICAL FIX: Initialize Location Component only once, then check permissions ---
            // The blue dot showing the user's *current device* location is independent of owner/collector markers.
            // It just shows where the *user* is.
            mapView.location.apply {
                locationPuck = LocationPuck2D(
                    bearingImage = ContextCompat.getDrawable(this@OrderTrackingActivity, R.drawable.ic_location_pin)?.toBitmap(96, 96)?.let { ImageHolder.from(it) },
                    scaleExpression = null
                )
                enabled = true // Enable the puck
                pulsingEnabled = true
            }
            checkLocationPermissions(false) // Just check permissions for the component, don't move map
        }
    }

    // --- CRITICAL FIX: getLastKnownLocation should NOT move camera or add markers ---
    @SuppressLint("MissingPermission")
    private fun getLastKnownLocation(moveCamera: Boolean) {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                val currentPoint = Point.fromLngLat(it.longitude, it.latitude)
                Log.d("OrderTracking", "Device last known location: ${currentPoint.latitude()}, ${currentPoint.longitude()}")
                // REMOVED: Implicit camera move or marker add here.
                // The camera is managed by moveCameraToPoints based on owner/collector.
                // The user's location puck is handled by Mapbox.location component.
            }
        }
    }

    private fun checkLocationPermissions(bool: Boolean) {
        val fineLocationGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseLocationGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (fineLocationGranted && coarseLocationGranted) {
            enableLocationComponent()
            getLastKnownLocation(false)
        } else {
            requestLocationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    @SuppressLint("MissingPermission") // Permissions are checked before calling this
    private fun enableLocationComponent() {
        mapView.location.enabled = true
        mapView.location.pulsingEnabled = true
    }

    private fun addOrUpdateMarker(point: Point, isCollector: Boolean = false) {
        val manager = pointAnnotationManager
        if (manager == null) {
            mapView.post { addOrUpdateMarker(point, isCollector) } // Retry after slight delay
            return
        }

        // Determine which avatar URL to use
        val avatarUrl: String? = if (isCollector) collector?.avtUrl else owner?.avtUrl
        val placeholderResId = R.drawable.default_avatar// Generic fallback

        // --- Use Coil to load the avatar, then create Mapbox marker ---
        lifecycleScope.launch {
            val imageLoader = RetrofitClient.imageLoader // Use our existing Coil ImageLoader
            val request = ImageRequest.Builder(this@OrderTrackingActivity)
                .data(avatarUrl)
                .transformations(CircleCropTransformation()) // Ensure it's round
                .size(120, 120) // Target size for the marker bitmap (adjust as needed)
                .allowHardware(false) // Sometimes needed for bitmap issues on older devices
                .target { drawable ->
                    // Convert drawable to bitmap with a background for clarity if transparent
                    val bitmap = convertDrawableToBitmap(drawable, 120)
                    bitmap?.let { bmp ->
                        val options = PointAnnotationOptions().withPoint(point).withIconImage(bmp)

                        try {
                            if (isCollector) {
                                collectorMarker?.let { manager.delete(it) }
                                collectorMarker = manager.create(options)
                                Log.d("OrderTracking", "Collector avatar marker set at ${point.latitude()}, ${point.longitude()}")
                            } else {
                                ownerMarker?.let { manager.delete(it) }
                                ownerMarker = manager.create(options)
                                Log.d("OrderTracking", "Owner avatar marker set at ${point.latitude()}, ${point.longitude()}")
                            }
                            moveCameraToPoints(ownerPickupLocation, collectorCurrentLocation) // Adjust camera after marker update
                        } catch (e: Exception) {
                            Log.e("OrderTracking", "Failed to create marker: ${e.message}")
                        }
                    }
                }
                .listener(
                    onError = { _, errorResult ->
                        Log.e("OrderTracking", "Coil failed to load avatar for marker: ${errorResult.throwable.message}")
                        // Fallback to default icon if avatar fails to load
                        createDefaultIconMarker(point, isCollector, manager)
                        moveCameraToPoints(ownerPickupLocation, collectorCurrentLocation)
                    }
                )
                .build()

            imageLoader.enqueue(request)
        }
    }

    private fun createDefaultIconMarker(point: Point, isCollector: Boolean, manager: PointAnnotationManager) {
        val iconRes = if (isCollector) R.drawable.default_avatar else R.drawable.ic_location_pin
        val drawable = ContextCompat.getDrawable(this, iconRes)
        val bmp = drawable?.toBitmap(96, 96) // Use a larger default size

        bmp?.let {
            val options = PointAnnotationOptions().withPoint(point).withIconImage(it)
            try {
                if (isCollector) {
                    collectorMarker?.let { manager.delete(it) }
                    collectorMarker = manager.create(options)
                } else {
                    ownerMarker?.let { manager.delete(it) }
                    ownerMarker = manager.create(options)
                }
            } catch (e: Exception) {
                Log.e("OrderTracking", "Failed to create fallback marker: ${e.message}")
            }
        }
    }

    private fun convertDrawableToBitmap(sourceDrawable: Drawable, size: Int): Bitmap? { // Removed backgroundColor parameter
        if (sourceDrawable == null) return null // Added null check if sourceDrawable is nullable
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val borderWidth = 4f // Adjust border width as desired (e.g., 2f, 4f, 6f)
        val borderPaint = Paint().apply {
            color = ContextCompat.getColor(this@OrderTrackingActivity, R.color.black) // Use your defined black color
            style = Paint.Style.STROKE
            strokeWidth = borderWidth
            isAntiAlias = true
        }

        // 3. Draw the border circle
        val center = size / 2f
        val radius = size / 2f - borderWidth / 2f // Radius slightly smaller for border to be fully inside
        canvas.drawCircle(center, center, radius, borderPaint)

        val avatarInset = borderWidth // Inset by the border width
        sourceDrawable.setBounds(
            avatarInset.toInt(),
            avatarInset.toInt(),
            (size - avatarInset).toInt(),
            (size - avatarInset).toInt()
        )
        sourceDrawable.draw(canvas)
        return bitmap
    }


    private fun moveCameraToPoints(point1: Point?, point2: Point?) {
        val points = mutableListOf<Point>()
        point1?.let { points.add(it) }
        point2?.let { points.add(it) }

        if (points.isEmpty()) {
            Log.d("OrderTracking", "moveCameraToPoints: No valid points to move camera to.")
            return
        }

        val mapboxMap = mapView.getMapboxMap()

        // --- NEW: Check if map style is loaded before moving camera ---
        if (mapboxMap.style == null) {
            Log.w("OrderTracking", "moveCameraToPoints: Map style not loaded yet. Skipping camera move.")
            return
        }

        if (points.size == 1) {
            Log.d("OrderTracking", "moveCameraToPoints: Centering on single point: ${points.first().latitude()}, ${points.first().longitude()}")
            mapboxMap.setCamera(
                CameraOptions.Builder()
                    .center(points.first())
                    .zoom(14.0)
                    .build()
            )
        } else {
            Log.d("OrderTracking", "moveCameraToPoints: Fitting bounds for two points: ${point1?.latitude()},${point1?.longitude()} and ${point2?.latitude()},${point2?.longitude()}")
            val cameraPadding = resources.getDimensionPixelSize(R.dimen.map_camera_padding).toDouble()
            val edgeInsets = EdgeInsets(cameraPadding, cameraPadding, cameraPadding, cameraPadding)

            // Try to fit bounds, catch potential exceptions if coordinates are degenerate
            try {
                val cameraOptions: CameraOptions = mapboxMap.cameraForCoordinates(points, edgeInsets)
                mapboxMap.setCamera(cameraOptions)
            } catch (e: Exception) {
                Log.e("OrderTracking", "Error fitting camera to coordinates: ${e.message}", e)
                // Fallback to default camera move or center on one point
                points.firstOrNull()?.let {
                    mapboxMap.setCamera(
                        CameraOptions.Builder()
                            .center(it)
                            .zoom(14.0)
                            .build()
                    )
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateCollectorUI(collector: CollectorPublic) {
        // Load Collector's Avatar
        binding.ivCollectorAvatarTracking.load(collector.avtUrl, RetrofitClient.imageLoader) {
            crossfade(true)
            transformations(CircleCropTransformation())
            placeholder(R.drawable.default_avatar) // Use a generic placeholder
            error(R.drawable.bg_image_error) // Fallback on error
        }

        // Display Collector's Name
        binding.tvCollectorNameTracking.text = "${collector.fullName ?: "Collector"} is on the way"

        Log.d("OrderTracking", "Collector UI updated: Name=${collector.fullName}, Rating=${collector.averageRating}")
    }

    private fun connectWebSocket(orderId: String) {
        val token = getSharedPreferences("app_prefs", Context.MODE_PRIVATE).getString("auth_token", null)
        if (token == null) {
            Toast.makeText(this, "Authentication token missing.", Toast.LENGTH_SHORT).show()
            return
        }

        val wsUrl = "ws://160.30.192.11:8000/api/ws/ws/track/$orderId/owner"
        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("Authorization", "Bearer $token")
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                Log.d("WebSocket", "Opened: ${response.message}")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("WebSocket", "Received: $text")
                try {
                    val map = gson.fromJson(text, Map::class.java) as Map<String, Any?>
                    val lat = (map["lat"] as? Number)?.toDouble()
                    val lng = (map["lng"] as? Number)?.toDouble()

                    if (lat != null && lng != null) {
                        collectorCurrentLocation = Point.fromLngLat(lng, lat)
                        runOnUiThread {
                            addOrUpdateMarker(collectorCurrentLocation!!, isCollector = true)
                            // --- NEW: Calculate distance and update UI ---
                            ownerPickupLocation?.let { ownerPoint ->
                                val distance = TurfMeasurement.distance(collectorCurrentLocation!!, ownerPoint, TurfConstants.UNIT_KILOMETERS)
                                val etaMinutes = (distance * 3).roundToLong() // Simple calculation: 3 mins per km

                                binding.tvEta.text = "ETA: $etaMinutes min"
                                binding.tvDistanceRemaining.text = "Remaining distance: ${String.format(Locale.ROOT, "%.1f", distance)} km"
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("WebSocket", "Parse error: ${e.message}", e)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebSocket", "Closing: $code / $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                Log.e("WebSocket", "Failure: ${t.message}", t)
            }
        })
    }

    // --- REMOVED: getRouteForOrderFromBackend() ---
    // --- REMOVED: drawRouteOnMapFromPolyline() ---

    // --- MODIFIED: updateRouteInfoUI is now updateDistanceInfo and called from WebSocket ---
    @SuppressLint("SetTextI18n")
    private fun updateDistanceInfo(collectorPoint: Point, ownerPoint: Point) {
        val distanceKm = TurfMeasurement.distance(collectorPoint, ownerPoint, TurfConstants.UNIT_KILOMETERS)
        val etaMinutes = (distanceKm * 3).roundToLong() // Simple calculation: 3 mins per km (adjust as needed)

        binding.tvEta.text = "ETA: $etaMinutes min"
        binding.tvDistanceRemaining.text = "Remaining distance: ${String.format(Locale.ROOT, "%.1f", distanceKm)} km"
    }

    private fun fetchCollectorDetails(orderId: String) {
        // Only fetch if collector details aren't already included in the initial OrderPublic
        if (collector != null) {
            updateCollectorUI(collector!!) // Use already included collector
            return
        }

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getOrderCollector(orderId)
                if (response.isSuccessful && response.body() != null) {
                    collector = response.body()
                    collector?.let {
                        updateCollectorUI(it)
                    }
                } else {
                    Log.e("OrderTracking", "Failed to fetch collector details: ${response.code()} - ${response.errorBody()?.string()}")
                    binding.tvCollectorNameTracking.text = "Collector on the way (Unknown)"
                    binding.ivCollectorAvatarTracking.setImageResource(R.drawable.default_avatar)
                }
            } catch (e: Exception) {
                Log.e("OrderTracking", "Error fetching collector details: ${e.message}", e)
                binding.tvCollectorNameTracking.text = "Collector on the way (Error)"
                binding.ivCollectorAvatarTracking.setImageResource(R.drawable.default_avatar)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocket?.close(1000, "App Destroyed")
        okHttpClient.dispatcher.cancelAll()
        mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }
}