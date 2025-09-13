package com.example.vaiche_driver.fragment

import android.graphics.drawable.Drawable
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
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.signature.ObjectKey
import com.example.vaiche_driver.R
import com.example.vaiche_driver.adapter.ReviewAdapter
import com.example.vaiche_driver.model.UserPublic
import com.example.vaiche_driver.viewmodel.ProfileViewModel

class ProfileFragment : Fragment() {

    companion object {
        const val TAG = "ProfileFragment"
    }

    private val viewModel: ProfileViewModel by viewModels()

    private lateinit var reviewAdapter: ReviewAdapter
    private var progressBar: ProgressBar? = null

    // Signature tạm (nếu Settings muốn ép Glide load lại avatar mới)
    private var forceAvatarSignature: String? = null

    /** Cho Settings gọi trực tiếp khi update xong (ép refresh + bust cache avatar nếu có signature) */
    fun refreshNow(signature: String? = null) {
        forceAvatarSignature = signature
        viewModel.refresh()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_profile, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        progressBar = view.findViewById(R.id.progress_bar_profile)

        setupViews(view)
        observeViewModel()
    }

    // Mỗi lần vào màn (trở lại từ Settings hoặc từ nơi khác) -> luôn load lại
    override fun onResume() {
        super.onResume()
        viewModel.loadInitialProfileData(force = true)
    }

    private fun setupViews(view: View) {
        val reviewsRecyclerView = view.findViewById<RecyclerView>(R.id.recycler_view_reviews)
        reviewAdapter = ReviewAdapter()
        val layoutManager = LinearLayoutManager(context)
        reviewsRecyclerView.layoutManager = layoutManager
        reviewsRecyclerView.adapter = reviewAdapter

        // Pagination đơn giản
        reviewsRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
                if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount && firstVisibleItemPosition >= 0) {
                    viewModel.loadMoreReviews()
                }
            }
        })

        // Nút vào Settings
        view.findViewById<ImageView>(R.id.iv_edit_profile).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, SettingsFragment(), SettingsFragment::class.java.simpleName)
                .addToBackStack(null)
                .commit()
        }
    }

    private fun observeViewModel() {
        // Profile
        viewModel.userProfile.observe(viewLifecycleOwner) { user ->
            user?.let { bindUserInfo(it) }
        }

        // Stats
        viewModel.userStats.observe(viewLifecycleOwner) { stats ->
            stats?.let {
                view?.findViewById<TextView>(R.id.tv_rating_value)?.text = "%.1f".format(it.averageRating)
                view?.findViewById<TextView>(R.id.tv_satisfy_value)?.text = "${it.satisfactionRate}%"
                view?.findViewById<TextView>(R.id.tv_cancel_value)?.text = "${it.cancellationRate}%"
            }
        }

        // Reviews
        viewModel.reviews.observe(viewLifecycleOwner) { reviews ->
            reviewAdapter.submitList(reviews)
        }

        // Loading
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            progressBar?.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        // Error
        viewModel.errorMessage.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { message ->
                Toast.makeText(context, "Error: $message", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun bindUserInfo(user: UserPublic) {
        val root = view ?: return

        val avatar = root.findViewById<ImageView>(R.id.iv_avatar)
        val nameBelowAvatar = root.findViewById<TextView>(R.id.tv_name_below_avatar)
        val phoneText = root.findViewById<TextView>(R.id.tv_phone_value)
        val fullNameText = root.findViewById<TextView>(R.id.tv_fullname_value)
        val emailText = root.findViewById<TextView>(R.id.tv_email_value)
        val genderText = root.findViewById<TextView>(R.id.tv_gender_value)
        val dobText = root.findViewById<TextView>(R.id.tv_dob_value)

        // Log kiểm tra nếu cần
        // android.util.Log.d("ProfileFragment", "avatarUrl = ${user.avatarUrl}")

        // Chỉ set signature khi thật sự có forceAvatarSignature để tránh miss cache không cần thiết
        val request = Glide.with(this)
            .load(user.avatarUrl ?: R.drawable.ic_user)
            .placeholder(R.drawable.ic_user)
            .error(R.drawable.ic_user)
            .circleCrop()
            .timeout(10_000)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    e?.logRootCauses("ProfileAvatar")
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean = false
            })

        forceAvatarSignature?.let { sig ->
            request.signature(ObjectKey(sig))
        }

        request.into(avatar)
        // Dùng xong signature thì xoá (để các lần sau dùng cache bình thường)
        forceAvatarSignature = null

        nameBelowAvatar.text = user.fullName
        phoneText.text = user.phoneNumber
        fullNameText.text = user.fullName
        emailText.text = user.email
        genderText.text = user.gender ?: "Not specified"
        dobText.text = user.birthDate ?: "Not specified"

        // Ẩn vì backend chưa có
        root.findViewById<TextView>(R.id.tv_plate_label).visibility = View.GONE
        root.findViewById<TextView>(R.id.tv_plate_value).visibility = View.GONE
    }
}
