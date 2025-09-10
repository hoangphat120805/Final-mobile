package com.example.vaiche_driver.data.repository

import com.example.vaiche_driver.data.common.safeApiCall
import com.example.vaiche_driver.data.network.ApiService
import com.example.vaiche_driver.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap

class ProfileRepository(
    private val apiProvider: () -> ApiService
) {
    private val api get() = apiProvider()

    private var cachedAvgRating: Float? = null
    private val userNameCache = ConcurrentHashMap<String, String>()

    fun invalidateCache() {
        cachedAvgRating = null
        userNameCache.clear()
    }

    suspend fun getUserProfile(): Result<UserPublic> = withContext(Dispatchers.IO) {
        safeApiCall { api.getMe() }
    }

    suspend fun getReviewsPage(
        page: Int,
        pageSize: Int = 5,
        fetchOwnerName: Boolean = true
    ): Result<Pair<List<Review>, Float>> = withContext(Dispatchers.IO) {
        try {
            val allOrders =
                safeApiCall { api.getMyOrders() }.getOrElse { return@withContext Result.failure(it) }
            val completed = allOrders.filter { it.status == OrderStatusApi.COMPLETED }

            val from = (page - 1) * pageSize
            if (from >= completed.size) {
                val avg = getOrComputeAverageRating(completed)
                return@withContext Result.success(emptyList<Review>() to avg)
            }

            val to = minOf(from + pageSize, completed.size)
            val pageOrders = completed.subList(from, to)

            val uiList = mutableListOf<Review>()
            for (od in pageOrders) {
                val rv = safeApiCall { api.getOrderReview(od.id) }.getOrNull() ?: continue
                val name = if (fetchOwnerName) resolveOwnerName(
                    od.id,
                    rv.userId
                ) else fallbackNameFromUserId(rv.userId)
                uiList += Review(
                    userName = name,
                    userAvatarInitial = name.firstInitial(),
                    rating = rv.rating,
                    timeAgo = rv.createdAt.toTimeAgoSafe(),
                    comment = rv.comment
                )
            }
            val avg = getOrComputeAverageRating(completed)
            Result.success(uiList to avg)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun getOrComputeAverageRating(allCompletedOrders: List<OrderPublic>): Float {
        cachedAvgRating?.let { return it }
        val ratings = mutableListOf<Int>()
        for (od in allCompletedOrders) {
            val rv = safeApiCall { api.getOrderReview(od.id) }.getOrNull()
            rv?.let { ratings += it.rating }
        }
        val avg = if (ratings.isNotEmpty()) ratings.sum().toFloat() / ratings.size else 0f
        cachedAvgRating = avg
        return avg
    }

    private suspend fun resolveOwnerName(orderId: String, userId: String): String {
        userNameCache[userId]?.let { return it }
        val owner = safeApiCall { api.getOrderOwner(orderId) }.getOrNull()
        val name =
            if (owner != null && owner.id == userId) owner.fullName else fallbackNameFromUserId(
                userId
            )
        userNameCache[userId] = name
        return name
    }

    private fun fallbackNameFromUserId(userId: String): String {
        val tail = if (userId.length >= 4) userId.takeLast(4) else userId
        return "User-$tail"
    }

    private fun String.firstInitial(): String = this.trim().firstOrNull()?.uppercase() ?: "?"

    private fun String.toTimeAgoSafe(): String = try {
        val instant: Instant = OffsetDateTime.parse(this).toInstant()
        val now = Instant.now()
        val d = Duration.between(instant, now).abs()
        val days = d.toDays()
        val hours = d.toHours()
        val minutes = d.toMinutes()
        when {
            days > 0 -> "$days days ago"
            hours > 0 -> "$hours hours ago"
            minutes > 0 -> "$minutes minutes ago"
            else -> "just now"
        }
    } catch (_: Exception) {
        this
    }
}