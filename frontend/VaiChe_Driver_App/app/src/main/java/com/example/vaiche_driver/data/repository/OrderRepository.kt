package com.example.vaiche_driver.data.repository

import com.example.vaiche_driver.data.network.ApiService
import com.example.vaiche_driver.data.network.RetrofitClient
import com.example.vaiche_driver.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OrderRepository(private val apiService: ApiService = RetrofitClient.instance) {

    // --- BIẾN LƯU TRỮ TRẠNG THÁI "ẢO" ---
    // Dùng Map để lưu trạng thái "delivering" ở phía client
    private val localStatusCache = mutableMapOf<String, OrderStatus>()

    /**
     * Lấy danh sách tóm tắt các đơn hàng từ API.
     * Sẽ kiểm tra cache để gán đúng trạng thái "delivering" nếu có.
     */
    suspend fun getSchedules(): Result<List<Schedule>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getMyOrders()
                if (response.isSuccessful) {
                    val schedulesFromApi = response.body()?.map { it.toSchedule() } ?: emptyList()

                    // Cập nhật trạng thái từ cache
                    val finalSchedules = schedulesFromApi.map { schedule ->
                        if (localStatusCache.containsKey(schedule.id)) {
                            schedule.copy(status = localStatusCache[schedule.id]!!)
                        } else {
                            schedule
                        }
                    }
                    Result.success(finalSchedules)
                } else {
                    Result.failure(Exception("API Error: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Lấy chi tiết đơn hàng và kiểm tra cache.
     */
    suspend fun getOrderDetail(orderId: String): Result<OrderDetail> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getOrderById(orderId)
                if (response.isSuccessful && response.body() != null) {
                    // CHUYỂN ĐỔI NGAY TẠI ĐÂY
                    val orderDetail = response.body()!!.toOrderDetail()
                    Result.success(orderDetail)
                } else {
                    Result.failure(Exception("API Error: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Chuyển trạng thái sang "delivering" CHỈ Ở PHÍA CLIENT.
     */
    suspend fun markAsDelivering(orderId: String): Result<Unit> {
        return withContext(Dispatchers.Main) { // Thao tác với cache, có thể dùng Main thread
            localStatusCache[orderId] = OrderStatus.delivering
            Result.success(Unit)
        }
    }

    /**
     * Hoàn thành đơn hàng. Sẽ gọi API và xóa cache.
     */
    suspend fun completeOrder(orderId: String, request: OrderCompletionRequest): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.completeOrder(orderId, request)
                if (response.isSuccessful) {
                    localStatusCache.remove(orderId) // Xóa trạng thái "ảo" sau khi đã hoàn thành thật
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to complete order: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Lấy danh sách các đơn hàng đang chờ ở gần.
     */
    suspend fun getNearbyOrders(lat: Double, lng: Double): Result<List<OrderPublic>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getNearbyOrders(lat, lng)
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("API Error: ${response.code()} - ${response.message()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Chấp nhận một đơn hàng.
     */
    suspend fun acceptOrder(orderId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.acceptOrder(orderId)
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to accept order: ${response.code()} - ${response.message()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Thêm các item vào một đơn hàng.
     */
    suspend fun addItemsToOrder(orderId: String, items: List<OrderItemCreate>): Result<OrderPublic> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.addItemsToOrder(orderId, items)
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Failed to add items: ${response.code()} - ${response.message()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}