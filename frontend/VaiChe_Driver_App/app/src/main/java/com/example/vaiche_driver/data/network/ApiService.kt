package com.example.vaiche_driver.data.network

import com.example.vaiche_driver.model.*
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

/**
 * Interface này định nghĩa tất cả các endpoint API mà ứng dụng sẽ tương tác.
 * Retrofit sẽ sử dụng interface này để tạo ra các lời gọi mạng thực tế.
 *
 * Ghi chú:
 * - Các endpoint yêu cầu token: gửi header "Authorization: Bearer <token>" qua OkHttp Interceptor.
 * - Upload ảnh đơn: backend yêu cầu đúng 2 key multipart là file1 và file2.
 * - /api/orders/{order_id}/complete trả 201 với body TransactionReadResponse.
 */
interface ApiService {

    //==================== AUTHENTICATION ====================

    /** Đăng nhập JSON body */
    @POST("/api/auth/login")
    suspend fun login(@Body userLoginRequest: UserLoginRequest): Response<TokenResponse>

    /** (Tùy chọn) OAuth2 password flow (form-encoded) */
    @FormUrlEncoded
    @POST("/api/auth/login/access-token")
    suspend fun loginAccessToken(
        @Field("username") username: String,
        @Field("password") password: String,
        @Field("grant_type") grantType: String? = "password",
        @Field("scope") scope: String = "",
        @Field("client_id") clientId: String? = null,
        @Field("client_secret") clientSecret: String? = null,
    ): Response<TokenResponse>

    @POST("/api/auth/collector/signup")
    suspend fun signup(@Body collectorRegisterRequest: CollectorRegisterRequest): Response<UserPublic>

    @POST("/api/otp/send-otp")
    suspend fun sendOtp(@Body otpRequest: OtpRequest): Response<Unit>

    @POST("/api/otp/verify-otp")
    suspend fun verifyOtp(@Body otpVerifyRequest: OtpVerifyRequest): Response<OtpVerifyResponse>

    @POST("/api/user/reset-password")
    suspend fun resetPassword(@Body resetPasswordRequest: ResetPasswordRequest): Response<Unit>

    //==================== USER PROFILE ====================

    /** Lấy thông tin user hiện tại (đã đăng nhập) */
    @GET("/api/user/me")
    suspend fun getMe(): Response<UserPublic>

    /** Đổi mật khẩu của tài khoản hiện tại */
    @PATCH("/api/user/me/password")
    suspend fun updatePassword(@Body body: UpdatePassword): Response<MessageResponse>

    /** Upload avatar (multipart) – key phải là "file" */
    @Multipart
    @POST("/api/user/upload/avatar")
    suspend fun uploadAvatar(@Part file: MultipartBody.Part): Response<MessageResponse>

    /** Lấy danh sách thông báo của tôi */
    @GET("/api/user/me/notifications")
    suspend fun getMyNotifications(): Response<List<UserNotification>>

    /** Đánh dấu 1 thông báo là đã đọc */
    @POST("/api/user/read/{notification_id}")
    suspend fun markNotificationAsRead(
        @Path("notification_id") notificationId: String
    ): Response<Unit>

    //==================== CATEGORY ====================

    /** Danh sách category để thêm item */
    @GET("/api/category/")
    suspend fun getCategories(): Response<List<CategoryPublic>>

    //==================== ORDERS ====================

    /** Danh sách đơn của current user (collector) */
    @GET("/api/orders/")
    suspend fun getMyOrders(): Response<List<OrderPublic>>

    /**
     * Nearby PENDING orders.
     * SỬA: trả về List<NearbyOrderPublic> và có tham số radius_km (optional).
     */
    @GET("/api/orders/nearby")
    suspend fun getNearbyOrders(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radius_km") radiusKm: Double = 5.0,
        @Query("limit") limit: Int = 10
    ): Response<List<NearbyOrderPublic>>

    /** Chi tiết đơn theo id (thêm optional flags theo spec) */
    @GET("/api/orders/{order_id}")
    suspend fun getOrderById(
        @Path("order_id") orderId: String,
        @Query("include_user") includeUser: Boolean? = null,
        @Query("include_collector") includeCollector: Boolean? = null
    ): Response<OrderPublic>

    /**
     * Collector nhận đơn: PENDING -> ACCEPTED.
     * SỬA: backend yêu cầu body OrderAcceptRequest, trả OrderAcceptResponse.
     */
    @POST("/api/orders/{order_id}/accept")
    suspend fun acceptOrder(
        @Path("order_id") orderId: String,
        @Body body: OrderAcceptRequest = OrderAcceptRequest()
    ): Response<OrderAcceptResponse>

    /**
     * Thêm 1 item vào đơn.
     * SỬA: path chuẩn của backend là /item (không phải /items).
     * (Giữ nguyên tên hàm theo yêu cầu)
     */
    @POST("/api/orders/{order_id}/item")
    suspend fun addItemsToOrder(
        @Path("order_id") orderId: String,
        @Body item: OrderItemCreate
    ): Response<OrderPublic>

    /** Cập nhật 1 item trong đơn */
    @PATCH("/api/orders/{order_id}/item/{order_item_id}")
    suspend fun updateOrderItem(
        @Path("order_id") orderId: String,
        @Path("order_item_id") orderItemId: String,
        @Body itemUpdate: OrderItemUpdate
    ): Response<OrderPublic>

    /** Xóa 1 item khỏi đơn */
    @DELETE("/api/orders/{order_id}/item/{order_item_id}")
    suspend fun deleteOrderItem(
        @Path("order_id") orderId: String,
        @Path("order_item_id") orderItemId: String
    ): Response<OrderPublic>

    /**
     * Upload ảnh đơn (pickup + dropoff).
     * THÊM: backend yêu cầu 2 file bắt buộc (file1, file2).
     */
    @Multipart
    @POST("/api/orders/{order_id}/upload/img")
    suspend fun uploadOrderImages(
        @Path("order_id") orderId: String,
        @Part file1: MultipartBody.Part,
        @Part file2: MultipartBody.Part
    ): Response<MessageResponse>

    /** Lấy route từ vị trí hiện tại (server tính) đến pickup của đơn */
    @GET("/api/orders/{order_id}/route")
    suspend fun getRouteForOrder(@Path("order_id") orderId: String): Response<RoutePublic>

    /**
     * Hoàn tất đơn & tạo transaction: -> COMPLETED.
     * SỬA: backend trả TransactionReadResponse (không phải Unit).
     */
    @POST("/api/orders/{order_id}/complete")
    suspend fun completeOrder(
        @Path("order_id") orderId: String,
        @Body completionRequest: OrderCompletionRequest
    ): Response<TransactionReadResponse>

    /** Info chủ đơn (owner) – collector có thể xem */
    @GET("/api/orders/{order_id}/owner")
    suspend fun getOrderOwner(@Path("order_id") orderId: String): Response<UserPublic>

    /** Info collector của đơn – phòng khi cần */
    @GET("/api/orders/{order_id}/collector")
    suspend fun getOrderCollector(@Path("order_id") orderId: String): Response<CollectorPublic>

    /** Review của đơn (collector xem) */
    @GET("/api/orders/{order_id}/review")
    suspend fun getOrderReview(
        @Path("order_id") orderId: String
    ): Response<ReviewPublic>
}
