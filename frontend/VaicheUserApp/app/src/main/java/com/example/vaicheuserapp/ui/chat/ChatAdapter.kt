package com.example.vaicheuserapp.ui.chat

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.vaicheuserapp.R
import com.example.vaicheuserapp.data.model.MessagePublic
import com.example.vaicheuserapp.databinding.ItemChatMessageReceivedBinding
import com.example.vaicheuserapp.databinding.ItemChatMessageSentBinding
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Timezone & Date Formatter (can be moved to common place)
private val VIETNAM_ZONE_ID = ZoneId.of("Asia/Ho_Chi_Minh")
private val BACKEND_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")

class ChatAdapter(private val currentUserId: String) :
    ListAdapter<MessagePublic, RecyclerView.ViewHolder>(MessageDiffCallback()) {

    private val VIEW_TYPE_SENT = 1
    private val VIEW_TYPE_RECEIVED = 2

    override fun getItemViewType(position: Int): Int {
        val message = getItem(position)
        return VIEW_TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_SENT) {
            val binding = ItemChatMessageSentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            SentMessageViewHolder(binding)
        } else {
            val binding = ItemChatMessageReceivedBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            ReceivedMessageViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        if (holder.itemViewType == VIEW_TYPE_SENT) {
            (holder as SentMessageViewHolder).bind(message)
        } else {
            (holder as ReceivedMessageViewHolder).bind(message)
        }
    }

    // Helper to add a single message (e.g., for optimistic update or new incoming message)
    fun addMessage(message: MessagePublic) {
        val currentList = currentList.toMutableList()
        currentList.add(message)
        submitList(currentList)
    }


    // ViewHolder for messages sent by the current user
    inner class SentMessageViewHolder(private val binding: ItemChatMessageSentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("SetTextI18n")
        fun bind(message: MessagePublic) {
            binding.tvMessageContent.text = message.content
            binding.tvMessageTime.text = formatTime(message.createdAt)
        }
    }

    // ViewHolder for messages received from other users
    inner class ReceivedMessageViewHolder(private val binding: ItemChatMessageReceivedBinding) :
        RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("SetTextI18n")
        fun bind(message: MessagePublic) {
            binding.tvMessageContent.text = message.content
            binding.tvMessageTime.text = formatTime(message.createdAt)
        }
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<MessagePublic>() {
        override fun areItemsTheSame(oldItem: MessagePublic, newItem: MessagePublic): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MessagePublic, newItem: MessagePublic): Boolean {
            // For chat, messages are immutable, so content comparison is often just checking equality
            return oldItem == newItem
        }
    }

    private fun formatTime(utcDateTimeString: String): String {
        return try {
            val localDateTime = LocalDateTime.parse(utcDateTimeString, BACKEND_DATETIME_FORMATTER)
            val dateTimeInVietnam = localDateTime.atZone(ZoneId.of("UTC")).withZoneSameInstant(VIETNAM_ZONE_ID)
            dateTimeInVietnam.format(DateTimeFormatter.ofPattern("HH:mm", Locale("vi", "VN"))) // e.g., "10:30"
        } catch (e: Exception) {
            "N/A"
        }
    }
}