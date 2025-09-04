package com.example.vaicheuserapp.ui.history

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.example.vaicheuserapp.R
import com.example.vaicheuserapp.data.model.OrderPublic
import com.example.vaicheuserapp.data.model.OrderStatus
import com.example.vaicheuserapp.data.network.RetrofitClient
import com.example.vaicheuserapp.databinding.ItemOrderHistoryBinding
import java.text.NumberFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import android.util.Log // Added for debugging dates

// Define the Vietnam Time Zone ID (can be moved to a common place later)
private val VIETNAM_ZONE_ID = ZoneId.of("Asia/Ho_Chi_Minh")
// Define the backend date-time formatter (assuming it's consistent)
private val BACKEND_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")


interface OnOrderClickListener {
    fun onOrderClick(order: OrderPublic)
}

class OrderHistoryAdapter(
    private val listener: OnOrderClickListener
) : ListAdapter<OrderPublic, OrderHistoryAdapter.OrderViewHolder>(OrderDiffCallback()) {

    inner class OrderViewHolder(private val binding: ItemOrderHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("SetTextI18n")
        fun bind(order: OrderPublic) {
            // --- Order Title ---
            // Concatenate names of first few items, or a generic title
            val orderTitle = order.items.take(2).map { "item" }.joinToString(", ").ifEmpty { when (order.status) {
                OrderStatus.PENDING -> "Pending Order"
                OrderStatus.ACCEPTED -> "Accepted Order"
                OrderStatus.COMPLETED -> "Completed Order"
                OrderStatus.CANCELLED -> "Cancelled Order"
            } } // Placeholder
            // TODO: If you have CategoryPublic objects available, you could fetch category names here.
            binding.tvOrderTitle.text = orderTitle

            // --- Order Date ---
            // Assuming order.createdAt is the source for the date
            binding.tvOrderDate.text = formatDateTime(order.createdAt)

            // --- Order Location ---
            binding.tvOrderLocation.text = order.pickupAddress

            val statusTextColor = when (order.status) {
                OrderStatus.PENDING -> R.color.status_pending_text
                OrderStatus.ACCEPTED -> R.color.status_accepted_text
                OrderStatus.COMPLETED -> R.color.status_completed_text
                OrderStatus.CANCELLED -> R.color.status_cancelled_text
            }
            binding.tvOrderTitle.setTextColor(ContextCompat.getColor(binding.root.context, statusTextColor))


            // --- Order Amount (Conditional Visibility) ---
            if (order.status == OrderStatus.COMPLETED) {
                val totalAmount = order.items.sumOf { it.quantity * it.pricePerUnit }
                val formattedAmount = NumberFormat.getCurrencyInstance(Locale("vi", "VN")).format(totalAmount)
                binding.tvOrderAmount.text = formattedAmount
                binding.tvOrderAmount.visibility = View.VISIBLE
            } else {
                binding.tvOrderAmount.visibility = View.GONE
            }


            // --- Background Color based on Status ---
            val backgroundColor = when (order.status) {
                OrderStatus.PENDING -> R.color.order_pending_background
                OrderStatus.ACCEPTED -> R.color.order_accepted_background
                OrderStatus.COMPLETED -> R.color.order_completed_background
                OrderStatus.CANCELLED -> R.color.order_cancelled_background
            }
            // Use isActivated state to trigger the selector drawable
            binding.llOrderItemBackground.backgroundTintList = ContextCompat.getColorStateList(binding.root.context, backgroundColor)
            // No, backgroundTintList is for tinting the drawable. For simple color, use setBackgroundColor
            binding.llOrderItemBackground.setBackgroundColor(ContextCompat.getColor(binding.root.context, backgroundColor))
            // The selector approach is for when state_activated or state_selected are managed directly.
            // For distinct colors, setBackgroundColor is clearer, or define a specific drawable for each state.
            // Let's stick to setBackgroundColor for simplicity with distinct colors.


            binding.root.setOnClickListener {
                listener.onOrderClick(order)
            }
        }

        private fun formatDateTime(utcDateTimeString: String): String {
            return try {
                val localDateTime = LocalDateTime.parse(utcDateTimeString, BACKEND_DATETIME_FORMATTER)
                val dateTimeInVietnam = localDateTime.atZone(ZoneId.of("UTC")).withZoneSameInstant(VIETNAM_ZONE_ID)
                val dateFormatter = DateTimeFormatter.ofPattern("HH:mm, dd/MM/yyyy", Locale("vi", "VN"))
                dateTimeInVietnam.format(dateFormatter)
            } catch (e: Exception) {
                Log.e("OrderHistoryAdapter", "Error parsing or formatting order date/time: $utcDateTimeString - ${e.message}", e)
                utcDateTimeString
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val binding = ItemOrderHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return OrderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        val order = getItem(position)
        holder.bind(order)
    }

    class OrderDiffCallback : DiffUtil.ItemCallback<OrderPublic>() {
        override fun areItemsTheSame(oldItem: OrderPublic, newItem: OrderPublic): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: OrderPublic, newItem: OrderPublic): Boolean {
            return oldItem == newItem
        }
    }
}