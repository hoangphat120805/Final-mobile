package com.example.vaiche_driver.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.vaiche_driver.R
import com.example.vaiche_driver.adapter.BottomNavVisibilityManager
import com.example.vaiche_driver.viewmodel.SharedViewModel

class RatingsFragment : Fragment() {

    private var orderId: String? = null
    private var currentRating = 0 // 0 nghĩa là chưa đánh giá
    private lateinit var stars: List<ImageView>

    private var visibilityManager: BottomNavVisibilityManager? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Kiểm tra xem Activity có triển khai interface không
        if (context is BottomNavVisibilityManager) {
            visibilityManager = context
            // Ngay lập tức ra lệnh ẩn thanh nav
            visibilityManager?.setBottomNavVisibility(false)
        }
    }

    override fun onDetach() {
        super.onDetach()
        // Ra lệnh cho Activity hiện lại thanh nav TRƯỚC KHI bị gỡ ra
        visibilityManager?.setBottomNavVisibility(true)
        // Dọn dẹp tham chiếu để tránh memory leak
        visibilityManager = null
    }

    // Truy cập SharedViewModel chung của Activity để báo cáo kết quả
    private val sharedViewModel: SharedViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            orderId = it.getString(ARG_ORDER_ID)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_ratings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Tìm các View cần tương tác
        val starsContainer = view.findViewById<LinearLayout>(R.id.rating_stars_container)
        val submitButton = view.findViewById<Button>(R.id.btn_submit_rating)
        val skipButton = view.findViewById<Button>(R.id.btn_skip_rating)
        val commentsEditText = view.findViewById<EditText>(R.id.et_comments)

        // Lấy danh sách các ImageView ngôi sao từ container
        stars = (0 until starsContainer.childCount).map { starsContainer.getChildAt(it) as ImageView }

        // Cài đặt sự kiện click cho các ngôi sao
        setupStarClickListeners()

        // Sự kiện cho nút "Submit"
        submitButton.setOnClickListener {
            val comment = commentsEditText.text.toString()

            if (currentRating == 0) {
                Toast.makeText(context, "Please select a rating", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // --- TODO: GỌI API ĐỂ GỬI ĐÁNH GIÁ (RATING VÀ COMMENT) LÊN BACKEND ---
            // ApiService.submitRating(orderId, currentRating, comment)
            // -------------------------------------------------------------------

            Toast.makeText(context, "Feedback submitted! Thank you.", Toast.LENGTH_LONG).show()

            // Báo cáo cho trung tâm điều khiển rằng quy trình đã KẾT THÚC
            sharedViewModel.onDeliveryFinished()

            // Quay về màn hình trước (thường là Dashboard sau khi pop)
            returnToPreviousScreen()
        }

        // Sự kiện cho nút "Skip"
        skipButton.setOnClickListener {
            // Dù bỏ qua, vẫn phải báo cáo là đã kết thúc quy trình
            sharedViewModel.onDeliveryFinished()

            returnToPreviousScreen()
        }
    }

    private fun setupStarClickListeners() {
        stars.forEachIndexed { index, imageView ->
            imageView.setOnClickListener {
                currentRating = index + 1
                updateStarsAppearance()
            }
        }
    }

    /**
     * Cập nhật giao diện của các ngôi sao dựa trên `currentRating`.
     */
    private fun updateStarsAppearance() {
        for (i in stars.indices) {
            val star = stars[i]
            if (i < currentRating) {
                // Các ngôi sao được chọn -> hiển thị ảnh đầy và tô màu vàng
                star.setImageResource(R.drawable.ic_star_filled)
            } else {
                // Các ngôi sao không được chọn -> hiển thị ảnh rỗng và không tô màu
                star.setImageResource(R.drawable.ic_star_outline)
            }
        }
    }

    /**
     * Quay về màn hình trước đó.
     * `popBackStack` sẽ hủy Fragment này và hiển thị lại Fragment nằm dưới nó trong back stack.
     */
    private fun returnToPreviousScreen() {
        parentFragmentManager.popBackStack()
    }

    companion object {
        private const val ARG_ORDER_ID = "order_id"

        fun newInstance(orderId: String) = RatingsFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_ORDER_ID, orderId)
            }
        }
    }
}

