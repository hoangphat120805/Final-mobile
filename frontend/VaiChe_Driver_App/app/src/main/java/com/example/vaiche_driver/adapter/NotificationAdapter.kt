package com.example.vaiche_driver.ui.notifications

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.ZoneId
import java.time.ZonedDateTime
import com.example.vaiche_driver.R
import android.util.Log
import com.example.vaiche_driver.fragment.NotificationsFragment
import com.example.vaiche_driver.model.UserNotification

// Click listener interface for the adapter
interface OnNotificationClickListener {
    fun onNotificationClick(notification: UserNotification)
}

class NotificationAdapter(
    private val onClick: OnNotificationClickListener
) : ListAdapter<UserNotification, NotificationAdapter.NotificationViewHolder>(NotificationDiffCallback()) {

    private val VIETNAM_ZONE_ID = ZoneId.of("Asia/Ho_Chi_Minh")
    private val BACKEND_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")

    inner class NotificationViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {

        fun bind(notification: UserNotification) {
            val tvTitle = itemView.findViewById<android.widget.TextView>(R.id.tv_notification_title)
            val tvSubtitle = itemView.findViewById<android.widget.TextView>(R.id.tv_notification_subtitle)
            val tvTime = itemView.findViewById<android.widget.TextView>(R.id.tv_notification_time)

            tvTitle.text = notification.title
            tvSubtitle.text = notification.message
            tvTime.text = formatTimeAgo(notification.createdAt)

            itemView.setOnClickListener {
                onClick.onNotificationClick(notification)
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
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return NotificationViewHolder(v)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class NotificationDiffCallback : DiffUtil.ItemCallback<UserNotification>() {
        override fun areItemsTheSame(oldItem: UserNotification, newItem: UserNotification): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: UserNotification, newItem: UserNotification): Boolean {
            return oldItem == newItem
        }
    }
}