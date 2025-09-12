package com.example.vaiche_driver.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.vaiche_driver.R
import com.example.vaiche_driver.model.UserPublic
import com.example.vaiche_driver.adapter.ReviewAdapter
import com.example.vaiche_driver.fragment.SettingsFragment
import com.example.vaiche_driver.viewmodel.ProfileViewModel

class ProfileFragment : Fragment() {

    // Khởi tạo ViewModel riêng cho màn hình này
    private val viewModel: ProfileViewModel by viewModels()

    // Khai báo Adapter cho RecyclerView của Review
    private lateinit var reviewAdapter: ReviewAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Nạp layout cho Fragment
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    // ⚠️ ĐÃ BỎ onResume() để tránh refresh mỗi lần quay lại gây load 2 lần
    // override fun onResume() { ... }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews(view)
        observeViewModel()

        // Điều hướng Settings khi bấm icon edit
        view.findViewById<ImageView>(R.id.iv_edit_profile).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, SettingsFragment())
                .addToBackStack(null)
                .commit()
        }

        // Tải dữ liệu lần đầu tiên khi Fragment được tạo
        if (savedInstanceState == null) {
            viewModel.loadInitialProfileData()
        }
    }

    private fun setupViews(view: View) {
        val reviewsRecyclerView = view.findViewById<RecyclerView>(R.id.recycler_view_reviews)
        reviewAdapter = ReviewAdapter() // Giả sử bạn đã có ReviewAdapter
        reviewsRecyclerView.adapter = reviewAdapter
        val layoutManager = LinearLayoutManager(context)
        reviewsRecyclerView.layoutManager = layoutManager

        // --- LOGIC PAGINATION (TẢI THÊM) ---
        reviewsRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                // Lấy thông tin về vị trí cuộn
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                // Kiểm tra xem đã cuộn gần đến cuối danh sách chưa và không đang tải
                if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount && firstVisibleItemPosition >= 0) {
                    // Yêu cầu ViewModel tải thêm dữ liệu
                    viewModel.loadMoreReviews()
                }
            }
        })
    }

    private fun observeViewModel() {
        val progressBar = view?.findViewById<ProgressBar>(R.id.progress_bar_profile)

        // Lắng nghe dữ liệu profile của người dùng từ API
        viewModel.userProfile.observe(viewLifecycleOwner) { user ->
            user?.let { bindUserInfo(it) }
        }

        // Lắng nghe dữ liệu thống kê (dữ liệu mẫu)
        viewModel.userStats.observe(viewLifecycleOwner) { stats ->
            stats?.let {
                view?.findViewById<TextView>(R.id.tv_rating_value)?.text = "%.1f".format(it.averageRating)
                view?.findViewById<TextView>(R.id.tv_satisfy_value)?.text = "${it.satisfactionRate}%"
                view?.findViewById<TextView>(R.id.tv_cancel_value)?.text = "${it.cancellationRate}%"
            }
        }

        // Lắng nghe danh sách đánh giá
        viewModel.reviews.observe(viewLifecycleOwner) { reviews ->
            reviewAdapter.submitList(reviews)
        }

        // Lắng nghe trạng thái loading
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            progressBar?.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        // Lắng nghe thông báo lỗi
        viewModel.errorMessage.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { message ->
                Toast.makeText(context, "Error: $message", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Hàm này chịu trách nhiệm điền dữ liệu người dùng (lấy từ API) vào các View.
     */
    private fun bindUserInfo(user: UserPublic) {
        val view = view ?: return

        val avatar = view.findViewById<ImageView>(R.id.iv_avatar)
        val nameBelowAvatar = view.findViewById<TextView>(R.id.tv_name_below_avatar)
        val phoneText = view.findViewById<TextView>(R.id.tv_phone_value)
        val fullNameText = view.findViewById<TextView>(R.id.tv_fullname_value)
        val emailText = view.findViewById<TextView>(R.id.tv_email_value)
        val genderText = view.findViewById<TextView>(R.id.tv_gender_value)
        val dobText = view.findViewById<TextView>(R.id.tv_dob_value)

        Glide.with(this)
            .load(user.avatarUrl)
            .placeholder(R.drawable.ic_user)
            .circleCrop()
            .into(avatar)

        nameBelowAvatar.text = user.fullName
        phoneText.text = user.phoneNumber
        fullNameText.text = user.fullName
        emailText.text = user.email
        genderText.text = user.gender ?: "Not specified"
        dobText.text = user.birthDate ?: "Not specified"

        // Ẩn plate_number vì backend chưa có
        view.findViewById<TextView>(R.id.tv_plate_label).visibility = View.GONE
        view.findViewById<TextView>(R.id.tv_plate_value).visibility = View.GONE
    }
}
