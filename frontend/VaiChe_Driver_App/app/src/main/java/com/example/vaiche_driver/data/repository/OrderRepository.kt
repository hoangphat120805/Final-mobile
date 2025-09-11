package com.example.vaiche_driver.data.repository

import com.example.vaiche_driver.data.common.safeApiCall
import com.example.vaiche_driver.data.network.ApiService
import com.example.vaiche_driver.model.*
//import com.mapbox.api.directions.v5.DirectionsCriteria
//import com.mapbox.api.directions.v5.models.DirectionsRoute
//import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.geojson.utils.PolylineUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import retrofit2.HttpException
import retrofit2.Response

class OrderRepository(
    // Provider trả ApiService mới nhất mỗi lần dùng
    private val apiProvider: () -> ApiService
) {
    private val api get() = apiProvider()

    // Trạng thái "ảo" ở client (ví dụ ép Delivering sau khi pickup local)
    private val localStatusCache = mutableMapOf<String, OrderStatus>()

    /** Danh sách Schedule (map từ OrderPublic bằng extensions) */
    suspend fun getSchedules(): Result<List<Schedule>> = withContext(Dispatchers.IO) {
        safeApiCall { api.getMyOrders() }.map { orders ->
            orders.map { op ->
                val override = localStatusCache[op.id]
                op.toSchedule(statusOverride = override)
            }
        }
    }

    /** Lấy OrderPublic thô (nếu VM cần) */
    suspend fun getOrderById(orderId: String): Result<OrderPublic> = withContext(Dispatchers.IO) {
        safeApiCall {
            api.getOrderById(
                orderId = orderId,
                includeUser = true,
                includeCollector = true
            )
        }
    }

    /** Lấy chi tiết đơn đã map sang OrderDetail (extensions) — có enrich từ Category, fallback khi lỗi */
    suspend fun getOrderDetail(orderId: String): Result<OrderDetail> = withContext(Dispatchers.IO) {
        try {
            coroutineScope {
                val orderDefer = async {
                    safeApiCall {
                        api.getOrderById(
                            orderId = orderId,
                            includeUser = true,
                            includeCollector = true
                        )
                    }
                }
                val catsDefer = async {
                    // Categories lỗi thì fallback empty list, không fail cả màn
                    safeApiCall { api.getCategories() }.getOrElse { emptyList() }
                }

                val orderPublic = orderDefer.await().getOrElse { throw it }
                val categories = catsDefer.await()
                val override = localStatusCache[orderId]

                if (categories.isEmpty()) {
                    Result.success(orderPublic.toOrderDetailFallback(statusOverride = override))
                } else {
                    Result.success(
                        orderPublic.toOrderDetail(
                            categories = categories,
                            statusOverride = override
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Đánh dấu local delivering (không gọi API) */
    suspend fun markAsDelivering(orderId: String): Result<Unit> = withContext(Dispatchers.IO) {
        localStatusCache[orderId] = OrderStatus.delivering
        Result.success(Unit)
    }

    /** Hoàn tất đơn */
    suspend fun completeOrder(orderId: String, request: OrderCompletionRequest): Result<Unit> =
        withContext(Dispatchers.IO) {
            safeApiCall { api.completeOrder(orderId, request) }.map { Unit }.onSuccess {
                localStatusCache.remove(orderId)
            }
        }

    /** Nearby orders (PENDING) */
    suspend fun getNearbyOrders(
        lat: Double,
        lng: Double,
        radiusKm: Double = 50.0,
        limit: Int = 10
    ): Result<List<NearbyOrderPublic>> = withContext(Dispatchers.IO) {
        safeApiCall { api.getNearbyOrders(lat, lng, radiusKm, limit) }
    }

    /** Collector nhận đơn */
    suspend fun acceptOrder(orderId: String): Result<Unit> = withContext(Dispatchers.IO) {
        safeApiCall { api.acceptOrder(orderId) }.map { Unit }
    }

    /** Thêm item vào đơn */
    suspend fun addItemsToOrder(orderId: String, items: OrderItemCreate): Result<OrderPublic> =
        withContext(Dispatchers.IO) {
            safeApiCall { api.addItemsToOrder(orderId, items) }
        }

    /** Upload ảnh đơn (pickup & dropoff) */
    suspend fun uploadOrderImages(
        orderId: String,
        file1: MultipartBody.Part,
        file2: MultipartBody.Part
    ): Result<MessageResponse> = withContext(Dispatchers.IO) {
        safeApiCall { api.uploadOrderImages(orderId, file1, file2) }
    }

    /** Cập nhật item trong đơn */
    suspend fun updateOrderItem(
        orderId: String,
        orderItemId: String,
        itemUpdate: OrderItemUpdate
    ): Result<OrderPublic> = withContext(Dispatchers.IO) {
        safeApiCall { api.updateOrderItem(orderId, orderItemId, itemUpdate) }
    }

    /** Xóa item khỏi đơn */
    suspend fun deleteOrderItem(
        orderId: String,
        orderItemId: String
    ): Result<OrderPublic> = withContext(Dispatchers.IO) {
        safeApiCall { api.deleteOrderItem(orderId, orderItemId) }
    }

    /** Lấy route collector -> pickup */
    suspend fun getRouteForOrder(orderId: String, lat: Double, lon: Double): Result<RoutePublic> =
        withContext(Dispatchers.IO) {
            safeApiCall { api.getRouteForOrder(orderId, lat, lon) }
        }


    /** Chủ đơn (owner) */
    suspend fun getOrderOwner(orderId: String): Result<UserPublic> =
        withContext(Dispatchers.IO) {
            safeApiCall { api.getOrderOwner(orderId) }
        }

    /** Collector của đơn */
    suspend fun getOrderCollector(orderId: String): Result<CollectorPublic> =
        withContext(Dispatchers.IO) {
            safeApiCall { api.getOrderCollector(orderId) }
        }

    suspend fun getActiveOrder(): Result<OrderPublic?> = withContext(Dispatchers.IO) {
        safeApiCall { api.getMyOrders() }.map { list ->
            list.firstOrNull { it.status == OrderStatusApi.ACCEPTED }
        }
    }


//    suspend fun getOrderRoute(orderId: String): Result<DirectionsRoute> = withContext(Dispatchers.IO) {
//        try {
//            val res = api.getRouteForOrder(orderId)
//            if (res.isSuccessful && res.body() != null) {
//                Result.success(res.body()!!.toDirectionsRoute(5))
//            } else {
//                Result.failure(Exception("Route error: ${res.code()}"))
//            }
//        } catch (e: Exception) {
//            Result.failure(e)
//        }
//    }


}
