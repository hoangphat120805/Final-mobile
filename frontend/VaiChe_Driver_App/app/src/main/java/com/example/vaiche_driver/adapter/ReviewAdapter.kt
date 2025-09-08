package com.example.vaiche_driver.adapter

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RatingBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.vaiche_driver.R
import com.example.vaiche_driver.model.Review
import kotlin.math.abs

/**
 * Adapter này được thiết kế để hiển thị danh sách các đánh giá (Review) trong RecyclerView.
 * Nó sử dụng ListAdapter để tối ưu hóa hiệu suất khi cập nhật danh sách.
 */
class ReviewAdapter : ListAdapter<Review, ReviewAdapter.ViewHolder>(ReviewDiffCallback()) {

    /**
     * ViewHolder chứa các tham chiếu đến các View bên trong một item layout (list_item_review.xml).
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val userInitial: TextView = view.findViewById(R.id.tv_user_initial)
        private val userName: TextView = view.findViewById(R.id.tv_user_name)
        private val ratingBar: RatingBar = view.findViewById(R.id.rating_bar)
        private val timestamp: TextView = view.findViewById(R.id.tv_timestamp)
        private val comment: TextView = view.findViewById(R.id.tv_comment)

        /**
         * Hàm này nhận một đối tượng `Review` và điền dữ liệu của nó vào các View.
         */
        fun bind(review: Review) {
            userName.text = review.userName
            userInitial.text = review.userAvatarInitial
            ratingBar.rating = review.rating.toFloat() // RatingBar yêu cầu kiểu Float
            timestamp.text = review.timeAgo
            comment.text = review.comment

            // Tự động tạo màu nền cho avatar dựa trên tên người dùng
            val background = userInitial.background as GradientDrawable
            background.setColor(getAvatarColor(review.userName))
        }

        /**
         * Hàm tiện ích để tạo ra một màu sắc ngẫu nhiên nhưng nhất quán cho mỗi tên.
         */
        private fun getAvatarColor(name: String): Int {
            // Danh sách các màu avatar được định sẵn
            val avatarColors = listOf(
                Color.parseColor("#8E44AD"), // Tím
                Color.parseColor("#2980B9"), // Xanh dương
                Color.parseColor("#27AE60"), // Xanh lá
                Color.parseColor("#F39C12"), // Vàng
                Color.parseColor("#D35400"), // Cam
                Color.parseColor("#C0392B")  // Đỏ
            )
            // Dùng hash code của tên để chọn một màu từ danh sách một cách nhất quán
            val index = abs(name.hashCode()) % avatarColors.size
            return avatarColors[index]
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_review, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val review = getItem(position)
        holder.bind(review)
    }
}

/**
 * DiffUtil giúp RecyclerView tính toán sự khác biệt giữa hai danh sách một cách hiệu quả.
 */
class ReviewDiffCallback : DiffUtil.ItemCallback<Review>() {
    /**
     * Kiểm tra xem hai item có phải là CÙNG MỘT ĐỐI TƯỢNG không.
     * Nếu Review có ID, chúng ta nên so sánh ID ở đây.
     * Hiện tại, chúng ta giả định không có hai review nào hoàn toàn giống hệt nhau.
     */
    override fun areItemsTheSame(oldItem: Review, newItem: Review): Boolean {
        // Trong thực tế, bạn sẽ so sánh oldItem.id == newItem.id
        return oldItem == newItem
    }

    /**
     * Kiểm tra xem nội dung của hai item có GIỐNG NHAU không.
     */
    override fun areContentsTheSame(oldItem: Review, newItem: Review): Boolean {
        // Data class tự động so sánh tất cả các thuộc tính
        return oldItem == newItem
    }
}