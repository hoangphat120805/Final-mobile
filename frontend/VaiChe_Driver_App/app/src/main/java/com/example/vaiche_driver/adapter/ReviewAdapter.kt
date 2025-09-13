package com.example.vaiche_driver.adapter

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RatingBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.vaiche_driver.R
import com.example.vaiche_driver.model.Review
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalQueries
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Adapter for displaying a list of reviews in RecyclerView.
 * Uses ListAdapter for efficient diffing and updating.
 */
class ReviewAdapter : ListAdapter<Review, ReviewAdapter.ViewHolder>(ReviewDiffCallback()) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val userInitial: TextView = view.findViewById(R.id.tv_user_initial)
        private val userName: TextView = view.findViewById(R.id.tv_user_name)
        private val ratingBar: RatingBar = view.findViewById(R.id.rating_bar)
        private val timestamp: TextView = view.findViewById(R.id.tv_timestamp)
        private val comment: TextView = view.findViewById(R.id.tv_comment)

        fun bind(review: Review) {
            userName.text = review.userName
            userInitial.text = review.userAvatarInitial
            ratingBar.rating = review.rating.toFloat()

            // Format timestamp instead of showing raw ISO string
            timestamp.text = formatReviewTime(review.timeAgo)

            comment.text = review.comment

            // Consistent background color based on username
            val background = userInitial.background as GradientDrawable
            background.setColor(getAvatarColor(review.userName))
        }

        private fun getAvatarColor(name: String): Int {
            val avatarColors = listOf(
                Color.parseColor("#8E44AD"), // Purple
                Color.parseColor("#2980B9"), // Blue
                Color.parseColor("#27AE60"), // Green
                Color.parseColor("#F39C12"), // Yellow/Orange
                Color.parseColor("#D35400"), // Orange
                Color.parseColor("#C0392B")  // Red
            )
            val index = abs(name.hashCode()) % avatarColors.size
            return avatarColors[index]
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_review, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val OUT_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale("en", "US"))

        /**
         * Convert raw ISO-8601 string into human readable form:
         * - "< 1 min" → "Just now"
         * - "< 60 mins" → "X minutes ago"
         * - "< 24 hours" → "X hours ago"
         * - "< 7 days" → "X days ago"
         * - Otherwise show "dd/MM/yyyy HH:mm"
         *
         * Supports:
         *  - 2025-09-08T10:10:04.296622Z
         *  - 2025-09-08T10:10:04.296+07:00
         *  - 2025-09-08T10:10:04 (no offset → assume UTC)
         */
        fun formatReviewTime(raw: String?): String {
            if (raw.isNullOrBlank()) return ""

            val zdtLocal = runCatching {
                val parsed = DateTimeFormatter.ISO_DATE_TIME.parse(raw)
                val offset = parsed.query(TemporalQueries.offset())
                val systemZone = ZoneId.systemDefault()
                if (offset != null) {
                    OffsetDateTime.from(parsed).atZoneSameInstant(systemZone)
                } else {
                    // No offset → treat as UTC
                    LocalDateTime.from(parsed)
                        .atOffset(ZoneOffset.UTC)
                        .atZoneSameInstant(systemZone)
                }
            }.getOrNull()

            zdtLocal ?: return raw.replace('T', ' ').take(19) // fallback

            val now = ZonedDateTime.now(zdtLocal.zone)
            val minutes = max(0, Duration.between(zdtLocal, now).toMinutes().toInt())

            return when {
                minutes < 1 -> "Just now"
                minutes < 60 -> "$minutes minutes ago"
                minutes < 60 * 24 -> "${minutes / 60} hours ago"
                minutes < 60 * 24 * 7 -> "${minutes / (60 * 24)} days ago"
                else -> zdtLocal.format(OUT_FMT)
            }
        }
    }
}

/**
 * DiffUtil for efficient list updates.
 */
class ReviewDiffCallback : DiffUtil.ItemCallback<Review>() {
    override fun areItemsTheSame(oldItem: Review, newItem: Review): Boolean {
        // Ideally compare IDs if Review has one
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: Review, newItem: Review): Boolean = oldItem == newItem
}
