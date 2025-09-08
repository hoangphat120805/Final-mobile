package com.example.vaiche_driver.data.repository

import com.example.vaiche_driver.data.network.ApiService
import com.example.vaiche_driver.data.network.RetrofitClient
import com.example.vaiche_driver.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap

class ProfileRepository(
    private val apiService: ApiService = RetrofitClient.instance
) {

    // ====== Caching đơn giản (trong vòng đời app) ======
    private var cachedAvgRating: Float? = null
    private val userNameCache = ConcurrentHashMap<String, String>() // userId -> fullName

    /** Xoá cache khi cần làm tươi lại */
    fun invalidateCache() {
        cachedAvgRating = null
        userNameCache.clear()
    }

    // ==================== PUBLIC APIS ====================

    suspend fun getUserProfile(): Result<UserPublic> = withContext(Dispatchers.IO) {
        try {
            val res = apiService.getMe()
            if (res.isSuccessful && res.body() != null) {
                Result.success(res.body()!!)
            } else {
                Result.failure(Exception("getMe() error: ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Trả về:
     *  - List<Review> (UI) cho trang `page` (mỗi trang `pageSize` review)
     *  - avgRating = trung bình tất cả review (đã cache lần đầu)
     *
     * Paging: thực hiện trên danh sách đơn COMPLETED của collector.
     */
    suspend fun getReviewsPage(
        page: Int,
        pageSize: Int = 5,
        fetchOwnerName: Boolean = true
    ): Result<Pair<List<Review>, Float>> = withContext(Dispatchers.IO) {
        try {
            // 1) Lấy toàn bộ đơn của collector
            val ordersRes = apiService.getMyOrders()
            if (!ordersRes.isSuccessful || ordersRes.body() == null) {
                return@withContext Result.failure(Exception("getMyOrders() error: ${ordersRes.code()}"))
            }
            val allOrders = ordersRes.body()!!

            // 2) Lọc COMPLETED
            val completed = allOrders.filter { it.status == OrderStatusApi.COMPLETED }

            // 3) Phân trang theo đơn hoàn tất
            val from = (page - 1) * pageSize
            if (from >= completed.size) {
                // Vẫn trả avgRating (từ cache hoặc tính lần đầu) để UI cập nhật
                val avg = getOrComputeAverageRating(allCompletedOrders = completed)
                return@withContext Result.success(emptyList<Review>() to avg)
            }
            val to = minOf(from + pageSize, completed.size)
            val pageOrders = completed.subList(from, to)

            // 4) Với mỗi đơn trang này → gọi /review
            val uiList = mutableListOf<Review>()
            for (od in pageOrders) {
                val rvRes = apiService.getOrderReview(od.id)
                if (rvRes.isSuccessful) {
                    val rp = rvRes.body()
                    if (rp != null) {
                        val name = if (fetchOwnerName) {
                            resolveOwnerName(od.id, rp.userId) // cố gắng lấy tên thật
                        } else {
                            fallbackNameFromUserId(rp.userId)
                        }
                        uiList += Review(
                            userName = name,
                            userAvatarInitial = name.firstInitial(),
                            rating = rp.rating,
                            timeAgo = rp.createdAt.toTimeAgoSafe(),
                            comment = rp.comment
                        )
                    }
                }
            }

            // 5) avg rating toàn bộ (cache lần đầu)
            val avg = getOrComputeAverageRating(allCompletedOrders = completed)

            Result.success(uiList to avg)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== INTERNAL HELPERS ====================

    /**
     * Tính hoặc lấy từ cache: trung bình rating của TẤT CẢ review
     * trên toàn bộ đơn COMPLETED của collector hiện tại.
     */
    private suspend fun getOrComputeAverageRating(
        allCompletedOrders: List<OrderPublic>
    ): Float {
        cachedAvgRating?.let { return it }

        // Gọi review cho tất cả completed orders để tính avg (tốn chi phí I/O lần đầu)
        val ratings = mutableListOf<Int>()
        for (od in allCompletedOrders) {
            try {
                val rvRes = apiService.getOrderReview(od.id)
                if (rvRes.isSuccessful) {
                    rvRes.body()?.let { ratings += it.rating }
                }
            } catch (_: Exception) {
                // bỏ qua lỗi từng đơn lẻ
            }
        }
        val avg = if (ratings.isNotEmpty()) ratings.sum().toFloat() / ratings.size else 0f
        cachedAvgRating = avg
        return avg
    }

    /**
     * Lấy tên thật của người review:
     *  - Ưu tiên cache theo userId.
     *  - Thử gọi /api/orders/{id}/owner để lấy UserPublic.fullName (nếu trùng userId).
     *  - Nếu không lấy được, fallback "User-xxxx".
     */
    private suspend fun resolveOwnerName(orderId: String, userId: String): String {
        // Cache hit
        userNameCache[userId]?.let { return it }

        return try {
            val ownerRes = apiService.getOrderOwner(orderId)
            val name = if (ownerRes.isSuccessful && ownerRes.body() != null) {
                val owner = ownerRes.body()!!
                // Xác nhận cùng userId
                if (owner.id == userId) owner.fullName
                else fallbackNameFromUserId(userId)
            } else {
                fallbackNameFromUserId(userId)
            }
            userNameCache[userId] = name
            name
        } catch (_: Exception) {
            fallbackNameFromUserId(userId)
        }
    }

    private fun fallbackNameFromUserId(userId: String): String {
        val tail = if (userId.length >= 4) userId.takeLast(4) else userId
        return "User-$tail"
    }

    // ==================== SMALL EXTENSIONS ====================

    private fun String.firstInitial(): String =
        this.trim().firstOrNull()?.uppercase() ?: "?"

    /**
     * Parse ISO-8601 → "x days/hours/minutes ago".
     * Nếu parse lỗi ⇒ trả nguyên chuỗi.
     */
    private fun String.toTimeAgoSafe(): String = try {
        val instant: Instant = OffsetDateTime.parse(this).toInstant()
        val now = Instant.now()
        val d = Duration.between(instant, now).abs()
        val days = d.toDays()
        val hours = d.toHours()
        val minutes = d.toMinutes()
        when {
            days > 0    -> "$days days ago"
            hours > 0   -> "$hours hours ago"
            minutes > 0 -> "$minutes minutes ago"
            else        -> "just now"
        }
    } catch (_: Exception) {
        this
    }
}
