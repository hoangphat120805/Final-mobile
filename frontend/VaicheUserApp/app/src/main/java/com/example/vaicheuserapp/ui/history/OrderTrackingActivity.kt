package com.example.vaicheuserapp.ui.history

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.RectF
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
import coil.transform.CircleCropTransformation
import com.example.vaicheuserapp.R
import com.example.vaicheuserapp.data.model.CollectorPublic
import com.example.vaicheuserapp.data.model.OrderPublic
import com.example.vaicheuserapp.data.model.RoutePublic
import com.example.vaicheuserapp.data.network.RetrofitClient
import com.example.vaicheuserapp.databinding.ActivityOrderTrackingBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.geojson.utils.PolylineUtils
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.ImageHolder
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.expressions.generated.Expression
import com.mapbox.maps.extension.style.layers.generated.LineLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin.Companion.ROUND
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.extension.style.sources.getSourceAs
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.getLayer
import com.mapbox.maps.plugin.LocationPuck2D
import java.util.Locale

class OrderTrackingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOrderTrackingBinding
    private lateinit var mapView: MapView
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var order: OrderPublic? = null
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

    // Route source/layer ids
    private val ROUTE_SOURCE_ID = "route-source-id"
    private val ROUTE_LAYER_ID = "route-layer-id"

    // Permissions launcher
    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            enableLocationComponent()
            getLastKnownLocation()
        } else {
            Toast.makeText(this, "Location permissions are required for tracking.", Toast.LENGTH_LONG).show()
            finish()
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
            ownerPickupLocation = Point.fromLngLat(currentOrder.location.longitude, currentOrder.location.latitude)

            // Add owner's marker once map is ready (we call addOrUpdateMarker later; it's safe to call now)
            // Initialize web socket and fetch collector info
            fetchCollectorDetails(currentOrder.id)
            connectWebSocket(currentOrder.id)

            // We request the route only when we have both points or when backend returns route for order
            // Optionally trigger an initial backend route fetch if backend already has a route for the order:
            getRouteForOrderFromBackend(currentOrder.id)
        } ?: run {
            Toast.makeText(this, "Order not found for tracking.", Toast.LENGTH_SHORT).show()
            finish()
        }
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
        binding.tvToolbarTitle.text = "Order Tracking"
    }

    private fun setupListeners() {
        binding.btnContactCollector.setOnClickListener {
            Toast.makeText(this, "Contact collector clicked", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupMapboxMap() {
        mapView.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS) { style ->
            // create annotation manager after style is loaded
            try {
                pointAnnotationManager = mapView.annotations.createPointAnnotationManager()
            } catch (e: Exception) {
                Log.e("OrderTracking", "Failed to create PointAnnotationManager: ${e.message}")
                pointAnnotationManager = null
            }

            // enable location component (puck)
            try {
                mapView.location.enabled = true
                mapView.location.pulsingEnabled = true
            } catch (e: Exception) {
                Log.w("OrderTracking", "Location plugin not available: ${e.message}")
            }

            // Add owner marker if we have the location already
            ownerPickupLocation?.let { addOrUpdateMarker(it, isCollector = false) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableLocationComponent() {
        mapView.location.apply {
            // Configure LocationComponentSettings with the 2D puck
            locationPuck = LocationPuck2D(
                bearingImage = ContextCompat.getDrawable(this@OrderTrackingActivity, R.drawable.ic_location_pin) as ImageHolder?,
                scaleExpression = null
            )
            // Enable the component
            enabled = true
            pulsingEnabled = true // Optional: show pulsing circle

        }
    }

    @SuppressLint("MissingPermission")
    private fun getLastKnownLocation() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                Log.d("OrderTracking", "Device last known location: ${it.latitude}, ${it.longitude}")
            }
        }
    }

    private fun addOrUpdateMarker(point: Point, isCollector: Boolean = false) {
        // Ensure annotation manager exists
        val manager = pointAnnotationManager
        if (manager == null) {
            // If not ready, queue a simple retry by posting to UI thread shortly after
            mapView.post {
                addOrUpdateMarker(point, isCollector)
            }
            return
        }

        // Choose icon
        val iconRes = if (isCollector) R.drawable.default_avatar else R.drawable.ic_location_pin
        val drawable = ContextCompat.getDrawable(this, iconRes)
        // Convert to bitmap scaled for visibility
        val bmp = drawable?.toBitmap(96, 96)

        val options = PointAnnotationOptions()
            .withPoint(point)

        bmp?.let { options.withIconImage(it) }

        try {
            if (isCollector) {
                collectorMarker?.let { manager.delete(it) }
                collectorMarker = manager.create(options)
                Log.d("OrderTracking", "Collector marker set at ${point.latitude()}, ${point.longitude()}")
            } else {
                ownerMarker?.let { manager.delete(it) }
                ownerMarker = manager.create(options)
                Log.d("OrderTracking", "Owner marker set at ${point.latitude()}, ${point.longitude()}")
            }
        } catch (e: Exception) {
            Log.e("OrderTracking", "Failed to create marker: ${e.message}")
        }

        // Move camera to show both points
        moveCameraToPoints(ownerPickupLocation, collectorCurrentLocation)
    }

    private fun moveCameraToPoints(point1: Point?, point2: Point?) {
        val points = mutableListOf<Point>()
        point1?.let { points.add(it) }
        point2?.let { points.add(it) }

        if (points.isEmpty()) return

        if (points.size == 1) {
            mapView.getMapboxMap().setCamera(
                CameraOptions.Builder()
                    .center(points.first())
                    .zoom(14.0)
                    .build()
            )
        } else {
            // padding in pixels
            val padPx = resources.getDimensionPixelSize(R.dimen.map_camera_padding).toDouble()

            // EdgeInsets(left, top, right, bottom)
            val edgeInsets = EdgeInsets(padPx, padPx, padPx, padPx)

            // get a CameraOptions that fits the coordinates with the given insets
            val cameraOptions: CameraOptions = mapView.getMapboxMap().cameraForCoordinates(points, edgeInsets)

            // apply the camera
            mapView.getMapboxMap().setCamera(cameraOptions)
        }
    }

    //
    // WebSocket tracking: receives collector {lat,lng} and updates marker + route fetch
    //
    private fun connectWebSocket(orderId: String) {
        val token = getSharedPreferences("app_prefs", Context.MODE_PRIVATE).getString("auth_token", null)
        if (token == null) {
            Toast.makeText(this, "Authentication token missing.", Toast.LENGTH_SHORT).show()
            return
        }

        val wsUrl = "ws://160.30.192.11:8000/ws/track/$orderId/owner"
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
                            // Tell backend to compute a route for the current collector location OR
                            // fetch route for order which backend may compute based on latest coords
                            getRouteForOrderFromBackend(order!!.id)
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
                runOnUiThread {
                    Toast.makeText(this@OrderTrackingActivity, "Tracking lost: ${t.message}", Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    //
    // Route / backend helpers
    //
    private fun getRouteForOrderFromBackend(orderId: String) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getRouteForOrder(orderId)
                if (response.isSuccessful && response.body() != null) {
                    val routePublic = response.body()!!
                    runOnUiThread {
                        // Draw route
                        drawRouteOnMapFromPolyline(routePublic.polyline)
                        updateRouteInfoUI(routePublic)
                    }
                } else {
                    Log.e("OrderTracking", "Failed to get route: ${response.code()} ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e("OrderTracking", "Error fetching route: ${e.message}", e)
            }
        }
    }

    // Draw or update a GeoJSON LineString layer from a polyline (encoded polyline string)
    private fun drawRouteOnMapFromPolyline(encodedPolyline: String) {
        try {
            val coords = PolylineUtils.decode(encodedPolyline, 5) // returns List<Point>
            if (coords.isEmpty()) return

            val lineString = LineString.fromLngLats(coords)
            val feature = Feature.fromGeometry(lineString)
            val featureCollection = FeatureCollection.fromFeatures(arrayOf(feature))

            mapView.getMapboxMap().getStyle { style ->
                // Source: add or update
                val existingSource = try {
                    style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID)
                } catch (e: Exception) {
                    null
                }

                if (existingSource == null) {
                    // create new source
                    val src = geoJsonSource(ROUTE_SOURCE_ID) {
                        featureCollection(featureCollection)
                    }
                    style.addSource(src)
                } else {
                    existingSource.featureCollection(featureCollection)
                }

                // Layer: add if doesn't exist
                val layerExists = try {
                    style.getLayer(ROUTE_LAYER_ID) != null
                } catch (e: Exception) {
                    false
                }

                if (!layerExists) {
                    val lineLayer = lineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID) {
                        lineCap(LineCap.ROUND)
                        lineJoin(LineJoin.ROUND)
                        lineWidth(6.0)
                        lineColor(ContextCompat.getColor(this@OrderTrackingActivity, R.color.teal_primary))
                    }
                    style.addLayer(lineLayer)
                }
            }
        } catch (e: Exception) {
            Log.e("OrderTracking", "Failed to draw route: ${e.message}", e)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateRouteInfoUI(route: RoutePublic) {
        val distanceKm = route.distanceMeters / 1000.0
        val durationSeconds = route.durationSeconds

        // Convert duration to more readable format
        val minutes = (durationSeconds / 60).toInt()
        val seconds = (durationSeconds % 60).toInt()

        binding.tvEta.text = "ETA: ${minutes} min ${seconds} sec" // Show minutes and seconds
        binding.tvDistanceRemaining.text = "Remaining distance: ${String.format(Locale.ROOT, "%.1f", distanceKm)} km" // Format to 1 decimal place
    }

    // Helper stub - fetch collector details if you need them (API client code)
    private fun fetchCollectorDetails(orderId: String) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getOrderCollector(orderId)
                if (response.isSuccessful && response.body() != null) {
                    collector = response.body()
                    collector?.let {
                        binding.tvCollectorNameTracking.text = "${it.fullName} is on the way"
                        binding.ivCollectorAvatarTracking.load(it.avtUrl, RetrofitClient.imageLoader) {
                            crossfade(true)
                            transformations(CircleCropTransformation())
                            placeholder(R.drawable.default_avatar)
                            error(R.drawable.bg_image_error)
                        }
                    }
                } else {
                    Log.e("OrderTracking", "Failed to fetch collector details: ${response.code()} - ${response.errorBody()?.string()}")
                    Toast.makeText(this@OrderTrackingActivity, "Failed to load collector info.", Toast.LENGTH_SHORT).show()
                    // Fallback to placeholder if fetching fails
                    binding.tvCollectorNameTracking.text = "Collector on the way"
                    binding.ivCollectorAvatarTracking.setImageResource(R.drawable.default_avatar)
                }
            } catch (e: Exception) {
                Log.e("OrderTracking", "Error fetching collector details: ${e.message}", e)
                Toast.makeText(this@OrderTrackingActivity, "Error loading collector info.", Toast.LENGTH_SHORT).show()
                // Fallback to placeholder on error
                binding.tvCollectorNameTracking.text = "Collector on the way"
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
