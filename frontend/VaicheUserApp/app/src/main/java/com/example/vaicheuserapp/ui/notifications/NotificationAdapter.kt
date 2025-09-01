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
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import androidx.core.content.ContextCompat
import com.example.vaicheuserapp.R
import android.graphics.Typeface
import android.util.Log
import android.widget.LinearLayout

// Click listener interface for the adapter
interface OnNotificationClickListener {
    fun onNotificationClick(notification: NotificationPublic)
}

class NotificationAdapter(
    private val listener: OnNotificationClickListener
) : ListAdapter<NotificationPublic, NotificationAdapter.NotificationViewHolder>(NotificationDiffCallback()) {

    private val VIETNAM_ZONE_ID = ZoneId.of("Asia/Ho_Chi_Minh")
    private val BACKEND_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")

    inner class NotificationViewHolder(private val binding: ItemNotificationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("SetTextI18n")
        fun bind(notification: NotificationPublic) {
            binding.tvNotificationTitle.text = notification.title
            binding.tvNotificationSubtitle.text = notification.message
            binding.tvNotificationTime.text = formatTimeAgo(notification.createdAt) // Calls fixed function

            binding.llNotificationItemBackground.isActivated = notification.isRead
            binding.tvNotificationTitle.setTypeface(null, if (notification.isRead) Typeface.NORMAL else Typeface.BOLD)
            binding.tvNotificationSubtitle.setTypeface(null, if (notification.isRead) Typeface.NORMAL else Typeface.BOLD)

            binding.root.setOnClickListener {
                listener.onNotificationClick(notification)
            }
        }

        // --- formatTimeAgo (CRITICAL FIX: Use LocalDateTime.parse with custom formatter) ---
        private fun formatTimeAgo(utcDateTimeString: String): String {
            return try {
                // 1. Parse the string into LocalDateTime using the custom formatter
                val localDateTime = LocalDateTime.parse(utcDateTimeString, BACKEND_DATETIME_FORMATTER)
                // 2. Assume this LocalDateTime is in UTC, then convert to ZonedDateTime in Vietnam's time zone
                val pastTimeInVietnam = localDateTime.atZone(ZoneId.of("UTC")).withZoneSameInstant(VIETNAM_ZONE_ID)
                val nowInVietnam = ZonedDateTime.now(VIETNAM_ZONE_ID)

                val minutes = ChronoUnit.MINUTES.between(pastTimeInVietnam, nowInVietnam)
                val hours = ChronoUnit.HOURS.between(pastTimeInVietnam, nowInVietnam)
                val days = ChronoUnit.DAYS.between(pastTimeInVietnam, nowInVietnam)
                val months = ChronoUnit.MONTHS.between(pastTimeInVietnam, nowInVietnam)
                val years = ChronoUnit.YEARS.between(pastTimeInVietnam, nowInVietnam)

                when {
                    minutes < 1 -> "just now"
                    minutes < 60 -> "$minutes minutes ago"
                    hours < 24 -> "$hours hours ago"
                    days < 30 -> "$days days ago"
                    months < 12 -> "$months months ago"
                    else -> "$years years ago"
                }
            } catch (e: Exception) {
                Log.e("NotificationAdapter", "Error parsing or formatting time ago: $utcDateTimeString - ${e.message}")
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