package com.example.vaiche_driver.data.repository

import com.example.vaiche_driver.data.network.ApiService
import com.example.vaiche_driver.data.network.RetrofitClient
import com.example.vaiche_driver.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody

class OrderRepository(
    private val apiService: ApiService = RetrofitClient.instance
) {
    // Trạng thái "ảo" ở client (ví dụ ép Delivering sau khi pickup local)
    private val localStatusCache = mutableMapOf<String, OrderStatus>()

    /** Danh sách Schedule (map từ OrderPublic bằng extensions) */
    suspend fun getSchedules(): Result<List<Schedule>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getMyOrders()
            if (response.isSuccessful) {
                val schedules = response.body()
                    ?.map { op ->
                        val override = localStatusCache[op.id]
                        op.toSchedule(statusOverride = override)
                    }
                    ?: emptyList()
                Result.success(schedules)
            } else {
                Result.failure(Exception("API Error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Lấy chi tiết đơn đã map sang OrderDetail (extensions) */
    suspend fun getOrderDetail(orderId: String): Result<OrderDetail> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getOrderById(
                orderId = orderId,
                includeUser = true,
                includeCollector = true
            )
            if (response.isSuccessful && response.body() != null) {
                val override = localStatusCache[orderId]
                Result.success(response.body()!!.toOrderDetail(statusOverride = override))
            } else {
                Result.failure(Exception("API Error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Lấy OrderPublic thô (nếu VM cần) */
    suspend fun getOrderById(orderId: String): Result<OrderPublic> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getOrderById(
                orderId = orderId,
                includeUser = true,
                includeCollector = true
            )
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("API Error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Đánh dấu local delivering (không gọi API) */
    suspend fun markAsDelivering(orderId: String): Result<Unit> = withContext(Dispatchers.Main) {
        localStatusCache[orderId] = OrderStatus.delivering
        Result.success(Unit)
    }

    /** Hoàn tất đơn */
    suspend fun completeOrder(orderId: String, request: OrderCompletionRequest): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.completeOrder(orderId, request)
                if (response.isSuccessful) {
                    localStatusCache.remove(orderId)
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to complete order: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Nearby orders (PENDING) */
    suspend fun getNearbyOrders(
        lat: Double,
        lng: Double,
        radiusKm: Double = 5.0,
        limit: Int = 10
    ): Result<List<NearbyOrderPublic>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getNearbyOrders(lat, lng, radiusKm, limit)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("API Error: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Collector nhận đơn */
    suspend fun acceptOrder(orderId: String): Result<Unit> = withContext(Dispatchers.IO) {
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

    /** Thêm item vào đơn */
    suspend fun addItemsToOrder(orderId: String, items: OrderItemCreate): Result<OrderPublic> =
        withContext(Dispatchers.IO) {
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

    /** Upload ảnh đơn (pickup & dropoff) */
    suspend fun uploadOrderImages(
        orderId: String,
        file1: MultipartBody.Part,
        file2: MultipartBody.Part
    ): Result<MessageResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.uploadOrderImages(orderId, file1, file2)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Upload images failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Cập nhật item trong đơn */
    suspend fun updateOrderItem(
        orderId: String,
        orderItemId: String,
        itemUpdate: OrderItemUpdate
    ): Result<OrderPublic> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.updateOrderItem(orderId, orderItemId, itemUpdate)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to update item: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Xóa item khỏi đơn */
    suspend fun deleteOrderItem(
        orderId: String,
        orderItemId: String
    ): Result<OrderPublic> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.deleteOrderItem(orderId, orderItemId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to delete item: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Lấy route collector -> pickup */
    suspend fun getRouteForOrder(orderId: String): Result<RoutePublic> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getRouteForOrder(orderId)
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Failed to get route: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Chủ đơn (owner) */
    suspend fun getOrderOwner(orderId: String): Result<UserPublic> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getOrderOwner(orderId)
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Failed to get owner: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Collector của đơn */
    suspend fun getOrderCollector(orderId: String): Result<CollectorPublic> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getOrderCollector(orderId)
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Failed to get collector: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
