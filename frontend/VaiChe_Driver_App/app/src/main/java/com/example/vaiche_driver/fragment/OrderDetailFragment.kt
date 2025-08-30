package com.example.vaiche_driver.fragment

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.vaiche_driver.R
import com.example.vaiche_driver.adapter.BottomNavVisibilityManager
import com.example.vaiche_driver.model.OrderDetail
import com.example.vaiche_driver.model.OrderStatus
import com.example.vaiche_driver.viewmodel.OrderDetailViewModel
import com.google.android.material.appbar.MaterialToolbar
import java.io.File
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Fragment này hiển thị toàn bộ thông tin chi tiết của một đơn hàng.
 * Nó sử dụng một ViewModel (`OrderDetailViewModel`) để quản lý dữ liệu và logic,
 * giúp cho Fragment chỉ tập trung vào việc hiển thị giao diện.
 */
class OrderDetailFragment : Fragment() {

    private var orderId: String? = null

    // Khởi tạo ViewModel. `by viewModels()` là cách chuẩn để làm việc này.
    private val viewModel: OrderDetailViewModel by viewModels()

    // --- LOGIC CHỤP ẢNH ---
    private var latestTmpUri: Uri? = null
    private var photoTarget: PhotoTarget? = null

    // Đăng ký launcher để gọi camera và nhận kết quả
    private val takeImageResult = registerForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
        if (isSuccess) {
            latestTmpUri?.let { uri ->
                // Khi chụp ảnh thành công, gọi hàm xử lý kết quả
                handlePhotoResult(uri)
            }
        }
    }

    // Enum để xác định mục tiêu chụp ảnh là Pick-up hay Drop-off
    enum class PhotoTarget { PICKUP, DROPOFF }
    // -----------------------

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
        // Nạp layout chính cho Fragment
        return inflater.inflate(R.layout.fragment_order_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // BƯỚC 1: "LẮNG NGHE" SỰ THAY ĐỔI DỮ LIỆU TỪ VIEWMODEL
        viewModel.orderDetail.observe(viewLifecycleOwner) { order ->
            if (order != null) {
                // Mỗi khi dữ liệu trong ViewModel thay đổi, hàm bind sẽ được gọi để vẽ lại giao diện
                bindDataToViews(view, order)
            } else {
                // Xử lý trường hợp không có dữ liệu (ví dụ: sau khi tải lần đầu)
            }
        }

        // BƯỚC 2: YÊU CẦU VIEWMODEL TẢI DỮ LIỆU LẦN ĐẦU
        // `savedInstanceState == null` để đảm bảo chỉ tải 1 lần, không tải lại khi xoay màn hình
        if (savedInstanceState == null) {
            viewModel.loadOrder(orderId)
        }
    }

    /**
     * Hàm trung tâm, nhận dữ liệu đơn hàng và cập nhật toàn bộ giao diện.
     */
    private fun bindDataToViews(view: View, order: OrderDetail) {
        // --- Tìm tất cả các View một lần ---
        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        val userAvatar = view.findViewById<ImageView>(R.id.iv_user_avatar)
        val userName = view.findViewById<TextView>(R.id.tv_user_name)
        val userPhone = view.findViewById<TextView>(R.id.tv_user_phone)
        val deliveryTime = view.findViewById<TextView>(R.id.tv_delivery_time)
        val startLocationName = view.findViewById<TextView>(R.id.tv_start_location_name_detail)
        val startLocationAddress = view.findViewById<TextView>(R.id.tv_start_location_address_detail)
        val endLocationName = view.findViewById<TextView>(R.id.tv_end_location_name_detail)
        val endLocationAddress = view.findViewById<TextView>(R.id.tv_end_location_address_detail)
        val userNote = view.findViewById<TextView>(R.id.tv_user_note)
        val itemsContainer = view.findViewById<LinearLayout>(R.id.container_order_items)
        val totalAmount = view.findViewById<TextView>(R.id.tv_total_amount)
        val totalWeight = view.findViewById<TextView>(R.id.tv_total_weight)
        val statusDot = view.findViewById<View>(R.id.status_dot)
        val statusText = view.findViewById<TextView>(R.id.tv_order_status)
        val actionButton = view.findViewById<Button>(R.id.btn_action)
        val pickupSection = view.findViewById<LinearLayout>(R.id.pickup_section)
        val dropoffSection = view.findViewById<LinearLayout>(R.id.dropoff_section)
        val pickupPhotoView = view.findViewById<View>(R.id.pickup_photo_view)
        val dropoffPhotoView = view.findViewById<View>(R.id.dropoff_photo_view)

        // --- Bind dữ liệu chung ---
        toolbar.title = "Order Detail ${order.id}"
        toolbar.setNavigationOnClickListener { activity?.onBackPressedDispatcher?.onBackPressed() }

        Glide.with(this).load(order.user.avatarUrl)
            .placeholder(R.drawable.ic_person_circle).error(R.drawable.ic_person_circle)
            .circleCrop().into(userAvatar)

        userName.text = order.user.fullName
        userPhone.text = order.user.phoneNumber
        deliveryTime.text = order.pickupTimestamp
        startLocationName.text = order.startLocationName
        startLocationAddress.text = order.startLocationAddress
        endLocationName.text = order.endLocationName
        endLocationAddress.text = order.endLocationAddress
        userNote.text = order.noteFromUser
        totalAmount.text = formatCurrency(order.totalAmount)
        totalWeight.text = "~${order.totalWeight.toInt()}kg"

        // ========== LOGIC HIỂN THỊ VÀ SỰ KIỆN CLICK ==========
        when (order.status) {
            OrderStatus.scheduled -> { // GIAI ĐOẠN PICK-UP
                statusDot.background = ContextCompat.getDrawable(requireContext(), R.drawable.green_dot_background)
                statusText.text = "On the way to pickup"
                statusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.green_text_color))

                pickupSection.visibility = View.VISIBLE
                dropoffSection.visibility = View.GONE
                setupPhotoView(pickupPhotoView, order.pickupPhotoUrl) {
                    photoTarget = PhotoTarget.PICKUP
                    takeImage()
                }

                actionButton.text = "Pick-Up"
                actionButton.isEnabled = order.pickupPhotoUrl != null
                actionButton.setOnClickListener {
                    viewModel.onPickupConfirmed()
                }
                actionButton.visibility = View.VISIBLE
            }
            OrderStatus.delivering -> { // GIAI ĐOẠN DROP-OFF
                statusDot.background = ContextCompat.getDrawable(requireContext(), R.drawable.orange_dot_background)
                statusText.text = "Delivering"
                statusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_delivering_text))

                pickupSection.visibility = View.VISIBLE
                dropoffSection.visibility = View.VISIBLE
                setupPhotoView(pickupPhotoView, order.pickupPhotoUrl, null)
                setupPhotoView(dropoffPhotoView, order.dropoffPhotoUrl) {
                    photoTarget = PhotoTarget.DROPOFF
                    takeImage()
                }

                actionButton.text = "Complete Delivery"
                actionButton.isEnabled = order.dropoffPhotoUrl != null
                actionButton.setOnClickListener {
                    viewModel.onDeliveryCompleted()
                    navigateToRatings(order.id)
                }
                actionButton.visibility = View.VISIBLE
            }
            OrderStatus.completed -> { // GIAI ĐOẠN HOÀN THÀNH
                statusDot.background = ContextCompat.getDrawable(requireContext(), R.drawable.blue_dot_background)
                statusText.text = "Done"
                statusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_done_text))

                pickupSection.visibility = View.VISIBLE
                dropoffSection.visibility = View.VISIBLE
                setupPhotoView(pickupPhotoView, order.pickupPhotoUrl, null)
                setupPhotoView(dropoffPhotoView, order.dropoffPhotoUrl, null)
                actionButton.visibility = View.GONE
            }
            else -> {
                statusText.text = order.status.name.replaceFirstChar { it.titlecase(Locale.getDefault()) }
                actionButton.visibility = View.GONE
                pickupSection.visibility = View.GONE
                dropoffSection.visibility = View.GONE
            }
        }

        // --- Bind danh sách Items ---
        itemsContainer.removeAllViews()
        val inflater = LayoutInflater.from(context)
        order.items.forEach { item ->
            val itemView = inflater.inflate(R.layout.include_order_item_row, itemsContainer, false)
            itemView.findViewById<TextView>(R.id.tv_item_name).text = "${item.categoryName} (${formatCurrency(item.pricePerUnit)}/${item.categoryUnit})"
            itemView.findViewById<TextView>(R.id.tv_item_quantity).text = "${item.quantity} ${item.categoryUnit}"
            itemView.findViewById<TextView>(R.id.tv_item_subtotal).text = formatCurrency(item.quantity * item.pricePerUnit)
            itemsContainer.addView(itemView)
        }
    }

    // --- CÁC HÀM XỬ LÝ ẢNH ---
    private fun takeImage() {
        lifecycleScope.launch {
            getTmpFileUri().let { uri ->
                latestTmpUri = uri
                takeImageResult.launch(uri)
            }
        }
    }

    private suspend fun getTmpFileUri(): Uri {
        return withContext(Dispatchers.IO) {
            val tmpFile = File.createTempFile("tmp_image_file", ".png", requireContext().cacheDir).apply {
                createNewFile()
                deleteOnExit()
            }
            FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", tmpFile)
        }
    }

    private fun handlePhotoResult(uri: Uri) {
        // Báo cáo kết quả cho ViewModel
        photoTarget?.let { target ->
            viewModel.onPhotoTaken(uri, target)
        }
    }

    // --- CÁC HÀM TIỆN ÍCH ---
    private fun setupPhotoView(photoView: View, photoUrl: String?, onPlaceholderClick: (() -> Unit)?) {
        val photoDisplay = photoView.findViewById<ImageView>(R.id.iv_photo_display)
        val placeholder = photoView.findViewById<LinearLayout>(R.id.container_photo_placeholder)

        if (photoUrl != null) {
            placeholder.visibility = View.GONE
            photoDisplay.visibility = View.VISIBLE
            Glide.with(this).load(photoUrl).centerCrop().into(photoDisplay)
        } else {
            placeholder.visibility = View.VISIBLE
            photoDisplay.visibility = View.GONE
            placeholder.setOnClickListener {
                onPlaceholderClick?.invoke()
            }
        }
    }

    private fun navigateToRatings(orderId: String) {
        val ratingsFragment = RatingsFragment.newInstance(orderId)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, ratingsFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun formatCurrency(amount: Double): String {
        return try {
            val format = NumberFormat.getCurrencyInstance(Locale("vi", "VN"))
            format.format(amount).replace("₫", "đ")
        } catch (e: Exception) { "${amount.toLong()}đ" }
    }

    companion object {
        private const val ARG_ORDER_ID = "order_id"

        @JvmStatic
        fun newInstance(orderId: String) =
            OrderDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_ORDER_ID, orderId)
                }
            }
    }

}