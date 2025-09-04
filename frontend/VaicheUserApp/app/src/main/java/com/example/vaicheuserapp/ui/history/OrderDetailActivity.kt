package com.example.vaicheuserapp.ui.history

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.transform.CircleCropTransformation
import com.example.vaicheuserapp.CategoryCache
import com.example.vaicheuserapp.R
import com.example.vaicheuserapp.data.model.OrderPublic
import com.example.vaicheuserapp.data.model.OrderStatus
import com.example.vaicheuserapp.data.model.TransactionMethod
import com.example.vaicheuserapp.data.network.RetrofitClient
import com.example.vaicheuserapp.databinding.ActivityOrderDetailBinding
import com.example.vaicheuserapp.databinding.ItemOrderDetailItemBinding // New Binding
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class OrderDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOrderDetailBinding
    private var currentOrder: OrderPublic? = null
    private var selectedRating: Int = 0

    // Define the Vietnam Time Zone ID (can be moved to a common place later)
    private val VIETNAM_ZONE_ID = ZoneId.of("Asia/Ho_Chi_Minh")
    // Define the backend date-time formatter (assuming it's consistent)
    private val BACKEND_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrderDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupListeners()

        // Retrieve the OrderPublic object from the Intent
        val order = getOrderFromIntent()

        order?.let {
            currentOrder = it
            displayOrderDetails(it)
        } ?: run {
            Toast.makeText(this, "Order details not found.", Toast.LENGTH_SHORT).show()
            finish() // Close if no data
        }
    }

    private fun getOrderFromIntent(): OrderPublic? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("EXTRA_ORDER", OrderPublic::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("EXTRA_ORDER")
        }
    }

    private fun setupToolbar() {
        binding.ivBackButton.setOnClickListener {
            finish() // Closes the current activity and goes back to HistoryFragment
        }
        // Title will be dynamically set by displayOrderDetails
    }

    private fun setupListeners() {
        binding.btnSendRating.setOnClickListener { sendRating() }
        setupRatingStars()
    }

    private fun setupRatingStars() {
        val stars = listOf(binding.star1, binding.star2, binding.star3, binding.star4, binding.star5)
        stars.forEachIndexed { index, starImageView ->
            starImageView.setOnClickListener {
                selectedRating = index + 1 // 1-based rating
                updateRatingStarsUI(selectedRating)
            }
        }
    }

    private fun updateRatingStarsUI(rating: Int) {
        val stars = listOf(binding.star1, binding.star2, binding.star3, binding.star4, binding.star5)
        stars.forEachIndexed { index, starImageView ->
            if (index < rating) {
                starImageView.setImageResource(R.drawable.ic_star) // Filled star
            } else {
                starImageView.setImageResource(R.drawable.ic_star_outline) // Outline star
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun displayOrderDetails(order: OrderPublic) {
        // --- Toolbar Title ---
        binding.tvToolbarTitle.text = formatDateTimeShort(order.createdAt) // Short date for toolbar

        // --- Collector Info (TEMPORARY) ---
        // TODO: Replace with real API call to get collector details (UserPublic)
        binding.ivCollectorAvatar.load(R.drawable.default_avatar) { // Generic avatar
            transformations(CircleCropTransformation())
            placeholder(R.drawable.default_avatar)
            error(R.drawable.bg_image_error)
        }
        binding.tvCollectorName.text = "BKSky" // Placeholder name
        binding.tvCollectorVehicle.text = "SH 150 83F1-2102" // Placeholder vehicle
        binding.tvCollectorRating.text = "4.96" // Placeholder rating

        // --- Order Items Detail ---
        binding.llOrderItemsContainer.removeAllViews() // Clear any old views
        var totalAmountCalculated = 0.0
        order.items.forEach { orderItem ->
            val itemBinding = ItemOrderDetailItemBinding.inflate(layoutInflater, binding.llOrderItemsContainer, false)
            val category = CategoryCache.getCategoryById(orderItem.categoryId)

            // Get price per unit from cache
            val pricePerUnit = category?.price ?: 0.0
            val itemName = category?.name ?: "Unknown Item"
            val unit = category?.unit ?: "unit"

            val itemValue = orderItem.quantity * pricePerUnit
            totalAmountCalculated += itemValue

            itemBinding.tvItemNameQty.text = "$itemName (${NumberFormat.getCurrencyInstance(Locale("vi", "VN")).format(pricePerUnit)}/${unit})\n${orderItem.quantity} ${unit}"
            itemBinding.tvItemSubtotal.text = NumberFormat.getCurrencyInstance(Locale("vi", "VN")).format(itemValue)

            binding.llOrderItemsContainer.addView(itemBinding.root)
        }

        // --- Total Amount ---
        binding.tvOrderTotalAmount.text = NumberFormat.getCurrencyInstance(Locale("vi", "VN")).format(totalAmountCalculated)

        // --- Payment Method ---
        // TODO: Get real transaction details, including payment method, from /api/transactions/order/{order_id}
        // For now, let's hardcode a common method or try to guess from order status
        // A better approach would be to make an API call to get the transaction.
        // For now, simulating based on data we would get from TransactionReadResponse
        val paymentMethod = TransactionMethod.WALLET // Simulate Wallet
        binding.tvPaymentMethod.text = paymentMethod.name.capitalize(Locale.ROOT) // "Momo", "Cash"
        val paymentIconRes = when (paymentMethod) {
            TransactionMethod.WALLET -> R.drawable.ic_wallet // You'll need this icon
            TransactionMethod.CASH -> R.drawable.ic_cash // You'll need this icon
        }
        binding.ivPaymentIcon.setImageResource(paymentIconRes)


        // --- Rating Section Visibility (Conditional) ---
        // Only show rating if order is completed and not yet rated (need API for "not yet rated")
        if (order.status == OrderStatus.COMPLETED) { // && !order.isRated (need this field from API)
            binding.llRatingSection.visibility = View.VISIBLE
            // TODO: If order is already rated, pre-fill stars and disable send button
        } else {
            binding.llRatingSection.visibility = View.GONE
        }
    }

    private fun sendRating() {
        if (selectedRating == 0) {
            Toast.makeText(this, "Please select a star rating.", Toast.LENGTH_SHORT).show()
            return
        }
        // TODO: Implement API call to send rating (e.g., PATCH /api/orders/{order_id}/rate)
        Toast.makeText(this, "Rating $selectedRating stars sent! (Simulated)", Toast.LENGTH_SHORT).show()
        binding.btnSendRating.visibility = View.GONE // Hide the button after sending
        // Optionally update UI to show "rated" state
        binding.llRatingSection.visibility = View.GONE // Hide rating section

    }


    private fun formatDateTimeShort(utcDateTimeString: String): String {
        return try {
            val localDateTime = LocalDateTime.parse(utcDateTimeString, BACKEND_DATETIME_FORMATTER)
            val dateTimeInVietnam = localDateTime.atZone(ZoneId.of("UTC")).withZoneSameInstant(VIETNAM_ZONE_ID)
            val dateFormatter = DateTimeFormatter.ofPattern("HH:mm, dd/MM/yyyy", Locale("vi", "VN"))
            dateTimeInVietnam.format(dateFormatter)
        } catch (e: Exception) {
            Log.e("OrderDetail", "Error parsing or formatting date/time: $utcDateTimeString - ${e.message}", e)
            utcDateTimeString
        }
    }
}