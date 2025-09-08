package com.example.vaiche_driver.data.repository

import com.example.vaiche_driver.data.network.ApiService
import com.example.vaiche_driver.data.network.RetrofitClient
import com.example.vaiche_driver.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProfileRepository(private val apiService: ApiService = RetrofitClient.instance) {

    suspend fun getUserProfile(): Result<UserPublic> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getMe()
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("API Error: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // --- CÁC HÀM TRẢ VỀ DỮ LIỆU MẪU ---
    fun getFakeUserStats(): UserStats {
        return UserStats(averageRating = 5.0f, satisfactionRate = 60, cancellationRate = 8)
    }

    suspend fun getFakeReviews(page: Int): Result<List<Review>> {
        // Giả lập độ trễ mạng
        kotlinx.coroutines.delay(1000)

        return try {
            val allReviews = listOf(
                Review("Nana Haley", "N", 5, "1 days ago", "ok ok ok"),
                Review("Alex Wall", "A", 5, "1 days ago", "Good service"),
                Review("John Doe", "J", 4, "2 days ago", "Fast pickup."),
                Review("Jane Smith", "J", 5, "3 days ago", "Very polite!"),
                // Thêm nhiều review giả ở đây...
                Review("User 5", "U", 3, "4 days ago", "Could be better."),
                Review("User 6", "U", 5, "5 days ago", "Excellent!")
            )

            // Logic phân trang đơn giản
            val pageSize = 2 // Mỗi trang lấy 2 review
            val startIndex = (page - 1) * pageSize

            if (startIndex >= allReviews.size) {
                Result.success(emptyList()) // Trả về danh sách rỗng nếu đã hết
            } else {
                val endIndex = minOf(startIndex + pageSize, allReviews.size)
                Result.success(allReviews.subList(startIndex, endIndex))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}