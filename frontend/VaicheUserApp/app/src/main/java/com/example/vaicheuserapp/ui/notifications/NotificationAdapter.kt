package com.example.vaicheuserapp.ui.notifications

import android.annotation.SuppressLint
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.vaicheuserapp.data.model.NotificationPublic
import com.example.vaicheuserapp.databinding.ItemNotificationBinding
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import androidx.core.content.ContextCompat
import com.example.vaicheuserapp.R
import android.graphics.Typeface
import android.widget.LinearLayout

// Click listener interface for the adapter
interface OnNotificationClickListener {
    fun onNotificationClick(notification: NotificationPublic)
}

class NotificationAdapter(
    private val listener: OnNotificationClickListener
) : ListAdapter<NotificationPublic, NotificationAdapter.NotificationViewHolder>(NotificationDiffCallback()) {

    inner class NotificationViewHolder(private val binding: ItemNotificationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        @RequiresApi(Build.VERSION_CODES.O)
        @SuppressLint("SetTextI18n")
        fun bind(notification: NotificationPublic) {
            binding.tvNotificationTitle.text = notification.title // Use new 'title' field
            binding.tvNotificationSubtitle.text = notification.message
            binding.tvNotificationTime.text = formatTimeAgo(notification.createdAt)

            // --- NEW: Set 'activated' state for background selector and text style ---
            binding.llNotificationItemBackground.isActivated = notification.isRead // <-- Controls background selector
            binding.tvNotificationTitle.setTypeface(null, if (notification.isRead) Typeface.NORMAL else Typeface.BOLD)
            binding.tvNotificationSubtitle.setTypeface(null, if (notification.isRead) Typeface.NORMAL else Typeface.BOLD)

            binding.root.setOnClickListener {
                listener.onNotificationClick(notification)
            }
        }

        @RequiresApi(Build.VERSION_CODES.O)
        private fun formatTimeAgo(dateTimeString: String): String {
            return try {
                val formatter = DateTimeFormatter.ISO_DATE_TIME
                val pastTime = LocalDateTime.parse(dateTimeString, formatter)
                val now = LocalDateTime.now()

                val minutes = ChronoUnit.MINUTES.between(pastTime, now)
                val hours = ChronoUnit.HOURS.between(pastTime, now)
                val days = ChronoUnit.DAYS.between(pastTime, now)
                val months = ChronoUnit.MONTHS.between(pastTime, now)
                val years = ChronoUnit.YEARS.between(pastTime, now)

                when {
                    minutes < 1 -> "just now"
                    minutes < 60 -> "$minutes minutes ago"
                    hours < 24 -> "$hours hours ago"
                    days < 30 -> "$days days ago"
                    months < 12 -> "$months months ago"
                    else -> "$years years ago"
                }
            } catch (e: Exception) {
                "Unknown time"
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val binding = ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NotificationViewHolder(binding)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notification = getItem(position)
        holder.bind(notification)
    }

    class NotificationDiffCallback : DiffUtil.ItemCallback<NotificationPublic>() {
        override fun areItemsTheSame(oldItem: NotificationPublic, newItem: NotificationPublic): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: NotificationPublic, newItem: NotificationPublic): Boolean {
            return oldItem == newItem
        }
    }
}