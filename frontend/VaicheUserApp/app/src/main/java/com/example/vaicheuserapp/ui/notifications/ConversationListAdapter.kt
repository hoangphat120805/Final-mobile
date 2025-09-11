package com.example.vaicheuserapp.ui.notifications

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.vaicheuserapp.R
import com.example.vaicheuserapp.data.model.ConversationWithLastMessage
import com.example.vaicheuserapp.data.model.UserPublic
import com.example.vaicheuserapp.data.network.RetrofitClient
import com.example.vaicheuserapp.databinding.ItemConversationBinding
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import androidx.lifecycle.lifecycleScope

// Timezone & Date Formatter (can be moved to common place)
private val VIETNAM_ZONE_ID = ZoneId.of("Asia/Ho_Chi_Minh")
private val BACKEND_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")

interface OnConversationClickListener {
    fun onConversationClick(conversation: ConversationWithLastMessage)
}

class ConversationListAdapter(
    private val listener: OnConversationClickListener,
    private val currentUserId: String, // Pass the current user's ID to identify "other" member

    private val fetchUserProfile: (String, (UserPublic?) -> Unit) -> Unit
) : ListAdapter<ConversationWithLastMessage, ConversationListAdapter.ConversationViewHolder>(ConversationDiffCallback()) {

    inner class ConversationViewHolder(private val binding: ItemConversationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        @RequiresApi(Build.VERSION_CODES.O)
        @SuppressLint("SetTextI18n")
        fun bind(conversation: ConversationWithLastMessage) {
            binding.tvConversationName.text = conversation.name ?: "Loading..."
            binding.tvLastMessageContent.text = conversation.lastMessage?.content ?: "No messages yet."

            conversation.lastMessage?.createdAt?.let {
                binding.tvLastMessageTime.text = formatTimeAgoShort(it)
            } ?: run {
                binding.tvLastMessageTime.text = ""
            }

            // --- CRITICAL FIX: Use the fetchUserProfile lambda provided by the fragment ---
            val otherMemberId = conversation.memberIds?.firstOrNull { it != currentUserId }

            if (otherMemberId != null) {
                binding.tvConversationName.text = "Loading..." // Set initial state
                binding.ivConversationAvatar.setImageResource(R.drawable.default_avatar) // Default placeholder

                // Call the lambda to fetch and cache user profile
                fetchUserProfile(otherMemberId) { fetchedUser ->
                    if (fetchedUser != null) {
                        binding.tvConversationName.text = fetchedUser.fullName
                        binding.ivConversationAvatar.load(fetchedUser.avtUrl, RetrofitClient.imageLoader) {
                            crossfade(true)
                            transformations(CircleCropTransformation())
                            placeholder(R.drawable.default_avatar)
                            error(R.drawable.bg_image_error)
                        }
                    } else {
                        // Fallback if fetching fails
                        binding.tvConversationName.text = conversation.name ?: "Chat Partner"
                        binding.ivConversationAvatar.setImageResource(R.drawable.default_avatar)
                    }
                }
            } else {
                // Group chat or no other member found
                binding.tvConversationName.text = conversation.name ?: "Group Chat"
                binding.ivConversationAvatar.setImageResource(R.drawable.default_avatar) // Generic group icon
            }

            binding.root.setOnClickListener {
                listener.onConversationClick(conversation)
            }
        }

        // Short time ago format for conversation list
        private fun formatTimeAgoShort(utcDateTimeString: String): String {
            return try {
                val localDateTime = LocalDateTime.parse(utcDateTimeString, BACKEND_DATETIME_FORMATTER)
                val pastTimeInVietnam = localDateTime.atZone(ZoneId.of("UTC")).withZoneSameInstant(VIETNAM_ZONE_ID)
                val nowInVietnam = ZonedDateTime.now(VIETNAM_ZONE_ID)

                val minutes = ChronoUnit.MINUTES.between(pastTimeInVietnam, nowInVietnam)
                val hours = ChronoUnit.HOURS.between(pastTimeInVietnam, nowInVietnam)
                val days = ChronoUnit.DAYS.between(pastTimeInVietnam, nowInVietnam)

                when {
                    minutes < 1 -> "now"
                    minutes < 60 -> "${minutes}m"
                    hours < 24 -> "${hours}h"
                    days < 7 -> "${days}d"
                    else -> pastTimeInVietnam.format(DateTimeFormatter.ofPattern("MMM dd")) // e.g., "Aug 27"
                }
            } catch (e: Exception) {
                "N/A"
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val binding = ItemConversationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ConversationViewHolder(binding)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        val conversation = getItem(position)
        holder.bind(conversation)
    }

    class ConversationDiffCallback : DiffUtil.ItemCallback<ConversationWithLastMessage>() {
        override fun areItemsTheSame(oldItem: ConversationWithLastMessage, newItem: ConversationWithLastMessage): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ConversationWithLastMessage, newItem: ConversationWithLastMessage): Boolean { // Renamed for consistency
            return oldItem == newItem
        }
    }
}