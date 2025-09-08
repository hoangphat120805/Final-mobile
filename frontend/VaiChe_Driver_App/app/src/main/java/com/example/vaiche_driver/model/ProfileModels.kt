package com.example.vaiche_driver.model

// Dữ liệu thống kê (dùng dữ liệu mẫu)
data class UserStats(
    val averageRating: Float,
    val satisfactionRate: Int, // Phần trăm
    val cancellationRate: Int // Phần trăm
)

// Dữ liệu cho một đánh giá (dùng dữ liệu mẫu)
data class Review(
    val userName: String,
    val userAvatarInitial: String, // Chữ cái đầu
    val rating: Int, // Số sao từ 1-5
    val timeAgo: String,
    val comment: String
)
