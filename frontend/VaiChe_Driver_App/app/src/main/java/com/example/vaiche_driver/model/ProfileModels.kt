package com.example.vaiche_driver.model

// Dữ liệu thống kê (dùng dữ liệu mẫu)
data class UserStats(
    val averageRating: Float,
    val satisfactionRate: Int, // Phần trăm
    val cancellationRate: Int // Phần trăm
)

// Dữ liệu cho một đánh giá (dùng dữ liệu mẫu)
data class Review(
    val userName: String,          // tên người viết review (suy ra tạm thời)
    val userAvatarInitial: String, // chữ cái đầu để vẽ avatar
    val rating: Int,               // 1..5
    val timeAgo: String,           // hiển thị "x days ago"
    val comment: String?           // nội dung
)