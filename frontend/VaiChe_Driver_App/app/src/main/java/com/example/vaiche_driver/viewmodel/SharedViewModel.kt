package com.example.vaiche_driver.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vaiche_driver.data.repository.OrderRepository
import com.example.vaiche_driver.model.DriverState
import com.example.vaiche_driver.model.NearbyOrderPublic
import com.example.vaiche_driver.model.OrderPublic
import com.example.vaiche_driver.model.Schedule
import com.example.vaiche_driver.model.localStatus
import com.example.vaiche_driver.model.toSchedule
import com.mapbox.geojson.Point
import kotlinx.coroutines.launch
import okhttp3.*
import kotlin.math.pow

class SharedViewModel : ViewModel() {

    private val TAG = "SharedVM"
    private val orderRepository =
        OrderRepository { com.example.vaiche_driver.data.network.RetrofitClient.instance }

    // ----------------- STATE GIỮ NGUYÊN -----------------
    private val _workingLocation = MutableLiveData<Pair<Double, Double>?>()
    val workingLocation: LiveData<Pair<Double, Double>?> = _workingLocation

    private val _driverState = MutableLiveData<DriverState>(DriverState.OFFLINE)
    val driverState: LiveData<DriverState> = _driverState

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<Event<String>>()
    val errorMessage: LiveData<Event<String>> = _errorMessage

    private val _activeOrder = MutableLiveData<Schedule?>()
    val activeOrder: LiveData<Schedule?> = _activeOrder

    private val _foundNewOrder = MutableLiveData<Event<NearbyOrderPublic>>()
    val foundNewOrder: LiveData<Event<NearbyOrderPublic>> = _foundNewOrder

    // Blacklist các order đã reject trong phiên FINDING
    private val rejectedOrderIds = mutableSetOf<String>()

    private val _orderAcceptedEvent = MutableLiveData<Event<Boolean>>()
    val orderAcceptedEvent: LiveData<Event<Boolean>> = _orderAcceptedEvent

    private val _orderRejectedEvent = MutableLiveData<Event<Boolean>>()
    val orderRejectedEvent: LiveData<Event<Boolean>> = _orderRejectedEvent

    // ----------------- ROUTE -----------------
    private val _routePoints = MutableLiveData<List<Point>>()
    val routePoints: LiveData<List<Point>> = _routePoints

    fun loadRoute(orderId: String, lat: Double, lon: Double) {
        viewModelScope.launch {
            val result = orderRepository.getRouteForOrder(orderId, lat, lon)
            result.onSuccess { route ->
                val pts = decodePolyline(route.polyline, 6)
                _routePoints.value = pts
            }.onFailure { e ->
                _errorMessage.value = Event("Route load failed: ${e.message}")
            }
        }
    }

    fun debugSetRoutePoints(points: List<Point>) {
        _routePoints.value = points
    }

    private fun decodePolyline(encoded: String, precision: Int = 6): List<Point> {
        val factor = 10.0.pow(precision.toDouble())
        var index = 0
        var lat = 0
        var lng = 0
        val coords = mutableListOf<Point>()

        while (index < encoded.length) {
            var result = 0
            var shift = 0
            var b: Int
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lat += dlat

            result = 0
            shift = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lng += dlng

            coords.add(Point.fromLngLat(lng / factor, lat / factor))
        }
        return coords
    }

    // ----------------- WEBSOCKET -----------------
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient()

    fun openWebSocket(orderId: String, token: String) {
        val request = Request.Builder()
            .url("ws://160.30.192.11:8000/api/ws/ws/track/$orderId/collector")
            .addHeader("Authorization", "Bearer $token")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d("WS", "Connected to order $orderId tracking")
            }
            override fun onMessage(ws: WebSocket, text: String) {
                Log.d("WS", "Message: $text")
            }
            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e("WS", "Error: ${t.message}")
            }
        })
        Log.d("WebSocket", "Opening WS for order=$orderId, token=$token")
    }

    fun sendLocation(lat: Double, lng: Double) {
        val json = """{"lat": $lat, "lng": $lng}"""
        webSocket?.send(json)
        Log.d("WebSocket", "Sent location: $json")
    }

    fun stopWebSocket() {
        webSocket?.close(1000, "stop")
        webSocket = null
    }

    override fun onCleared() {
        super.onCleared()
        stopWebSocket()
    }

    // ----------------- LOGIC CŨ (có chỉnh nhẹ) -----------------
    fun toggleOnlineStatus() {
        if (_driverState.value == DriverState.OFFLINE) {
            _driverState.value = DriverState.ONLINE
        } else if (_driverState.value != DriverState.DELIVERING) {
            _driverState.value = DriverState.OFFLINE
        }
    }

    /**
     * BẮT ĐẦU phiên tìm đơn => reset blacklist để tránh "kẹt" do reject tích lũy
     */
    fun onPlanConfirmed() {
        rejectedOrderIds.clear() // ✅ thêm
        _driverState.value = DriverState.FINDING_ORDER
    }

    fun onPlanConfirmed(lat: Double, lng: Double) {
        _workingLocation.value = Pair(lat, lng)
        rejectedOrderIds.clear() // ✅ thêm
        _driverState.value = DriverState.FINDING_ORDER
    }

    fun findNearbyOrder() {
        Log.d(TAG, "findNearbyOrder(): START")
        val location = _workingLocation.value
        if (location == null) {
            _errorMessage.value = Event("Working location is not set. Please set a plan.")
            _driverState.value = DriverState.OFFLINE
            return
        }

        viewModelScope.launch {
            Log.d(TAG, "findNearbyOrder(): calling repo at lat=${location.first}, lon=${location.second}")
            val res: Result<List<NearbyOrderPublic>> =
                orderRepository.getNearbyOrders(location.first, location.second)

            res.onSuccess { nearbyOrders ->
                Log.d(TAG, "findNearbyOrder(): DONE, size=${nearbyOrders.size}")

                // Ưu tiên những đơn chưa bị reject
                var newOrder = nearbyOrders.firstOrNull { it.id !in rejectedOrderIds }

                // ✅ Soft reset nếu tất cả đều bị blacklist (tránh "kẹt" dù size>0)
                if (newOrder == null && nearbyOrders.isNotEmpty() && rejectedOrderIds.isNotEmpty()) {
                    Log.d(TAG, "findNearbyOrder(): all candidates blacklisted -> soft reset blacklist")
                    rejectedOrderIds.clear()
                    newOrder = nearbyOrders.firstOrNull()
                }

                if (newOrder != null) {
                    _foundNewOrder.value = Event(newOrder)
                } else {
                    _errorMessage.value = Event("No new orders available in your area.")
                }
            }.onFailure { e ->
                Log.e(TAG, "findNearbyOrder(): ERROR ${e.message}", e)
                _errorMessage.value = Event(e.message ?: "Failed to find nearby orders.")
            }
        }
    }

    fun onOrderAccepted(newOrder: Schedule) {
        _activeOrder.value = newOrder
        _driverState.value = DriverState.DELIVERING
        rejectedOrderIds.clear()
        _orderAcceptedEvent.value = Event(true)
    }

    fun onOrderAccepted(newOrder: OrderPublic) {
        val schedule = newOrder.toSchedule(newOrder.localStatus())
        onOrderAccepted(schedule)
    }

    fun onOrderRejected(orderId: String) {
        rejectedOrderIds.add(orderId)
        _orderRejectedEvent.value = Event(true) // Dashboard nghe event này để restart tìm đơn
    }

    fun onDeliveryFinished() {
        _activeOrder.value = null
        _driverState.value = DriverState.OFFLINE
    }

    fun goOffline() {
        if (_driverState.value != DriverState.DELIVERING) {
            _driverState.value = DriverState.OFFLINE
        }
    }

    fun syncDriverStateOnLaunch(lat: Double? = null, lng: Double? = null) {
        viewModelScope.launch {
            val res = orderRepository.getActiveOrder()
            res.onSuccess { order ->
                if (order != null) {
                    _activeOrder.value = order.toSchedule(order.localStatus())
                    _driverState.value = DriverState.DELIVERING

                    if (lat != null && lng != null) {
                        loadRoute(order.id, lat, lng)
                    }
                } else {
                    _driverState.value = DriverState.OFFLINE
                }
            }.onFailure {
                _driverState.value = DriverState.OFFLINE
            }
        }
    }
}
