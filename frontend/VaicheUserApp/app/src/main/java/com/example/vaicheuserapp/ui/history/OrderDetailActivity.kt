package com.example.vaicheuserapp.ui.history

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
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
import com.example.vaicheuserapp.data.model.CollectorPublic // <-- NEW IMPORT
import com.example.vaicheuserapp.data.model.OrderPublic
import com.example.vaicheuserapp.data.model.OrderStatus
import com.example.vaicheuserapp.data.model.ReviewCreate // <-- NEW IMPORT
import com.example.vaicheuserapp.data.model.ReviewPublic // <-- NEW IMPORT
import com.example.vaicheuserapp.data.model.TransactionMethod
import com.example.vaicheuserapp.data.network.RetrofitClient
import com.example.vaicheuserapp.databinding.ActivityOrderDetailBinding
import com.example.vaicheuserapp.databinding.ItemOrderDetailItemBinding
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class OrderDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOrderDetailBinding
    private var currentOrder: OrderPublic? = null
    private var currentCollector: CollectorPublic? = null // To store collector data
    private var existingReview: ReviewPublic? = null // To store existing review data
    private var selectedRating: Int = 0

    private val VIETNAM_ZONE_ID = ZoneId.of("Asia/Ho_Chi_Minh")
    private val BACKEND_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrderDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupListeners()

        val order = getOrderFromIntent()

        order?.let {
            currentOrder = it
            displayOrderDetails(it)
            fetchCollectorDetails(it.id) // Fetch collector info
            fetchExistingReview(it.id) // Fetch review info
        } ?: run {
            Toast.makeText(this, "Order details not found.", Toast.LENGTH_SHORT).show()
            finish()
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
        binding.ivBackButton.setOnClickListener { finish() }
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
        binding.tvToolbarTitle.text = formatDateTimeShort(order.createdAt)

        // --- Order Items Detail ---
        binding.llOrderItemsContainer.removeAllViews()
        var totalAmountCalculated = 0.0
        order.items.forEach { orderItem ->
            val itemBinding = ItemOrderDetailItemBinding.inflate(layoutInflater, binding.llOrderItemsContainer, false)
            val category = CategoryCache.getCategoryById(orderItem.categoryId)

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

        // --- Payment Method (Placeholder for now, will fetch from transaction API) ---
        // TODO: This should come from TransactionReadResponse, for now using a default or simplified logic
        // We need the transaction for this.
        fetchTransactionDetails(order.id)


        // --- Rating Section Visibility (Managed by fetchExistingReview later) ---
        binding.llRatingSection.visibility = View.GONE // Initially hidden
        binding.llAlreadyRatedSection.visibility = View.GONE // Initially hidden
    }

    private fun fetchCollectorDetails(orderId: String) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getOrderCollector(orderId)
                if (response.isSuccessful && response.body() != null) {
                    currentCollector = response.body()
                    updateCollectorUI(currentCollector!!)
                } else {
                    Log.e("OrderDetail", "Failed to fetch collector details: ${response.code()} - ${response.errorBody()?.string()}")
                    Toast.makeText(this@OrderDetailActivity, "Failed to load collector info.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("OrderDetail", "Error fetching collector details: ${e.message}", e)
                Toast.makeText(this@OrderDetailActivity, "Error loading collector info.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateCollectorUI(collector: CollectorPublic) {
        binding.ivCollectorAvatar.load(collector.avtUrl, RetrofitClient.imageLoader) {
            crossfade(true)
            transformations(CircleCropTransformation())
            placeholder(R.drawable.default_avatar)
            error(R.drawable.bg_image_error)
        }
        binding.tvCollectorName.text = "${collector.fullName}"
        binding.tvCollectorPhone.text = collector.phoneNumber
        binding.tvCollectorRating.text = collector.averageRating?.let { String.format(Locale.ROOT, "%.2f", it) } ?: "N/A"
        binding.tvRatingHeader.text = "Rate your experience with ${collector.fullName}"
    }

    private fun fetchTransactionDetails(orderId: String) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getTransactionsByOrderId(orderId)
                if (response.isSuccessful && response.body() != null && response.body()!!.isNotEmpty()) {
                    val transaction = response.body()!!.first() // Assuming one transaction per order
                    updatePaymentUI(transaction.method)
                } else {
                    Log.e("OrderDetail", "Failed to fetch transaction details: ${response.code()} - ${response.errorBody()?.string()}")
                    Toast.makeText(this@OrderDetailActivity, "Failed to load payment info.", Toast.LENGTH_SHORT).show()
                    updatePaymentUI(TransactionMethod.CASH) // Fallback
                }
            } catch (e: Exception) {
                Log.e("OrderDetail", "Error fetching transaction details: ${e.message}", e)
                Toast.makeText(this@OrderDetailActivity, "Error loading payment info.", Toast.LENGTH_SHORT).show()
                updatePaymentUI(TransactionMethod.CASH) // Fallback
            }
        }
    }

    private fun updatePaymentUI(method: TransactionMethod) {
        binding.tvPaymentMethod.text = method.name.capitalize(Locale.ROOT)
        val paymentIconRes = when (method) {
            TransactionMethod.WALLET -> R.drawable.ic_wallet
            TransactionMethod.CASH -> R.drawable.ic_cash
        }
        binding.ivPaymentIcon.setImageResource(paymentIconRes)
    }

    private fun fetchExistingReview(orderId: String) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getOrderReview(orderId)
                if (response.isSuccessful && response.body() != null) {
                    existingReview = response.body()
                    displayExistingReview(existingReview!!)
                } else if (response.code() == 404) { // 404 Not Found usually means no review yet
                    Log.d("OrderDetail", "No existing review found for order $orderId.")
                    showRatingSection() // Show the rating input if no existing review
                } else {
                    Log.e("OrderDetail", "Failed to fetch existing review: ${response.code()} - ${response.errorBody()?.string()}")
                    Toast.makeText(this@OrderDetailActivity, "Failed to load review status.", Toast.LENGTH_SHORT).show()
                    showRatingSection() // Default to showing rating section on error
                }
            } catch (e: Exception) {
                Log.e("OrderDetail", "Error fetching existing review: ${e.message}", e)
                Toast.makeText(this@OrderDetailActivity, "Error loading review status.", Toast.LENGTH_SHORT).show()
                showRatingSection() // Default to showing rating section on error
            }
        }
    }

    private fun showRatingSection() {
        // Only show rating input if order is completed and no existing review
        if (currentOrder?.status == OrderStatus.COMPLETED && existingReview == null) {
            binding.llRatingSection.visibility = View.VISIBLE
            binding.llAlreadyRatedSection.visibility = View.GONE
        } else {
            binding.llRatingSection.visibility = View.GONE
        }
    }

    @SuppressLint("SetTextI18n")
    private fun displayExistingReview(review: ReviewPublic) {
        binding.llAlreadyRatedSection.visibility = View.VISIBLE
        binding.llRatingSection.visibility = View.GONE

        binding.tvRatedComment.text = review.comment ?: "No comment provided."

        // Dynamically add stars for existing rating
        binding.llRatedStarsDisplay.removeAllViews()
        for (i in 1..5) {
            val starImageView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.rating_star_size), // Define in dimens.xml
                    resources.getDimensionPixelSize(R.dimen.rating_star_size)
                ).apply {
                    marginStart = resources.getDimensionPixelSize(R.dimen.rating_star_spacing) // Define in dimens.xml
                }
                setImageResource(if (i <= review.rating) R.drawable.ic_star else R.drawable.ic_star_outline)
                contentDescription = "$i stars"
            }
            binding.llRatedStarsDisplay.addView(starImageView)
        }
        // Also update the header text
        binding.tvRatingHeader.text = "Your rating for ${currentCollector?.fullName ?: "collector"}"
    }


    private fun sendRating() {
        if (selectedRating == 0) {
            Toast.makeText(this, "Please select a star rating.", Toast.LENGTH_SHORT).show()
            return
        }
        val comment = binding.etRatingComment.text.toString().trim()
        val orderId = currentOrder?.id

        if (orderId == null) {
            Toast.makeText(this, "Order ID missing for rating.", Toast.LENGTH_SHORT).show()
            return
        }

        val reviewCreate = ReviewCreate(rating = selectedRating, comment = comment.ifEmpty { null })

        lifecycleScope.launch {
            try {
                binding.btnSendRating.isEnabled = false // Disable button during API call
                val response = RetrofitClient.instance.reviewCollectorForOrder(orderId, reviewCreate)
                if (response.isSuccessful && response.body() != null) {
                    Toast.makeText(this@OrderDetailActivity, "Rating sent successfully!", Toast.LENGTH_SHORT).show()
                    existingReview = response.body() // Update with the newly created review
                    displayExistingReview(existingReview!!) // Show the "Already Rated" section
                } else {
                    val errorBody = response.errorBody()?.string()
                    Toast.makeText(this@OrderDetailActivity, "Failed to send rating: ${response.code()} - ${errorBody ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("OrderDetail", "Error sending rating: ${e.message}", e)
                Toast.makeText(this@OrderDetailActivity, "Error sending rating: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.btnSendRating.isEnabled = true
            }
        }
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