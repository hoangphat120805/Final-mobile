package com.example.vaiche_driver.data.network

import com.example.vaiche_driver.model.CategoryPublic
import com.example.vaiche_driver.model.OrderCompletionRequest
import com.example.vaiche_driver.model.OrderItemCreate
import com.example.vaiche_driver.model.OrderPublic
import com.example.vaiche_driver.model.UserCreate
import com.example.vaiche_driver.model.UserPublic
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Interface này định nghĩa tất cả các endpoint API mà ứng dụng sẽ tương tác.
 * Retrofit sẽ sử dụng interface này để tạo ra các lời gọi mạng thực tế.
 */
interface ApiService {

    //==================== AUTH & USER ====================

    /**
     * Dùng để "mồi" dữ liệu tài khoản driver (chỉ dùng để demo).
     */
    @POST("/api/auth/collector/signup")
    suspend fun registerCollector(@Body userCreate: UserCreate): Response<UserPublic>

    // TODO: Thêm các hàm login, get/update user profile...

    //==================== CATEGORY ====================

    /**
     * Lấy danh sách tất cả các loại phế liệu.
     * Được dùng trong dialog "Add Item".
     */
    @GET("/api/category/")
    suspend fun getCategories(): Response<List<CategoryPublic>>

    //==================== ORDERS ====================

    /**
     * Lấy danh sách các đơn hàng đã được gán cho tài xế hiện tại.
     * Được dùng trong màn hình `ScheduleFragment`.
     */
    @GET("/api/orders/")
    suspend fun getMyOrders(): Response<List<OrderPublic>>

    /**
     * Tìm kiếm các đơn hàng `pending` ở gần.
     * Được dùng trong `DashboardFragment` khi ở trạng thái FINDING_ORDER.
     * @param lat Vĩ độ hiện tại của tài xế.
     * @param lng Kinh độ hiện tại của tài xế.
     */
    @GET("/api/orders/nearby")
    suspend fun getNearbyOrders(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("limit") limit: Int = 10 // Giới hạn số lượng kết quả
    ): Response<List<OrderPublic>> // Giả sử API trả về OrderPublic

    /**
     * Lấy thông tin chi tiết của một đơn hàng cụ thể.
     * Được dùng trong `OrderDetailFragment` và `NewOrderDialogFragment`.
     */
    @GET("/api/orders/{order_id}")
    suspend fun getOrderById(@Path("order_id") orderId: String): Response<OrderPublic>

    /**
     * Chấp nhận một đơn hàng.
     * Được gọi khi tài xế nhấn "Accept" trong `NewOrderDialogFragment`.
     */
    @POST("/api/orders/{order_id}/accept")
    suspend fun acceptOrder(@Path("order_id") orderId: String): Response<Unit> // Giả sử chỉ cần response code 200

    /**
     * Thêm các item (phế liệu) vào một đơn hàng.
     * Được gọi từ `AddItemDialogFragment` sau khi tài xế chọn và nhập số lượng.
     */
    @POST("/api/orders/{order_id}/items")
    suspend fun addItemsToOrder(
        @Path("order_id") orderId: String,
        @Body items: List<OrderItemCreate>
    ): Response<OrderPublic> // Trả về đơn hàng đã được cập nhật

    /**
     * Hoàn thành một đơn hàng.
     * Được gọi khi tài xế nhấn "Complete Delivery" trong `OrderDetailFragment`.
     */
    @POST("/api/orders/{order_id}/complete")
    suspend fun completeOrder(
        @Path("order_id") orderId: String,
        @Body completionRequest: OrderCompletionRequest
    ): Response<Unit> // Giả sử trả về Transaction, nhưng ta có thể bỏ qua body

    // TODO: Thêm các hàm upload ảnh cho Pick-up và Drop-off
    // @POST("/api/orders/{order_id}/pickup-photo") ...
    // @POST("/api/orders/{order_id}/dropoff-photo") ...

    // TODO: Thêm hàm gửi đánh giá (rating)
    // @POST("/api/orders/{order_id}/rate") ...
}