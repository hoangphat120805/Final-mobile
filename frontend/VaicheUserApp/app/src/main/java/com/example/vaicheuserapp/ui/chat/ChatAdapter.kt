package com.example.vaicheuserapp.ui.chat

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.vaicheuserapp.R
import com.example.vaicheuserapp.data.model.MessagePublic
import com.example.vaicheuserapp.data.model.UserPublic // <-- NEW IMPORT
import com.example.vaicheuserapp.data.network.RetrofitClient
import com.example.vaicheuserapp.databinding.ItemChatDateSeparatorBinding
import com.example.vaicheuserapp.databinding.ItemChatMessageReceivedBinding
import com.example.vaicheuserapp.databinding.ItemChatMessageSentBinding
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

// Timezone & Date Formatter (can be moved to common place)
private val VIETNAM_ZONE_ID = ZoneId.of("Asia/Ho_Chi_Minh")
private val BACKEND_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")

// --- CRITICAL FIX: Pass a map of user IDs to UserPublic objects ---
class ChatAdapter(
    private val currentUserId: String,
    private val chatParticipants: Map<String, UserPublic> // userId -> UserPublic
) : ListAdapter<MessagePublic, RecyclerView.ViewHolder>(MessageDiffCallback()) {

    private val VIEW_TYPE_SENT = 1
    private val VIEW_TYPE_RECEIVED = 2
    private val VIEW_TYPE_DATE_SEPARATOR = 3

    override fun getItemViewType(position: Int): Int {
        val message = getItem(position)
        return when (message.viewType) {
            MessagePublic.VIEW_TYPE_DATE_SEPARATOR -> VIEW_TYPE_DATE_SEPARATOR
            MessagePublic.VIEW_TYPE_MESSAGE -> {
                if (message.senderId == currentUserId) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
            }
            else -> throw IllegalArgumentException("Unknown view type for message at position $position")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_SENT -> {
                val binding = ItemChatMessageSentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                SentMessageViewHolder(binding)
            }
            VIEW_TYPE_RECEIVED -> {
                val binding = ItemChatMessageReceivedBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                ReceivedMessageViewHolder(binding)
            }
            VIEW_TYPE_DATE_SEPARATOR -> {
                val binding = ItemChatDateSeparatorBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                DateSeparatorViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        when (holder.itemViewType) {
            VIEW_TYPE_SENT -> (holder as SentMessageViewHolder).bind(message)
            VIEW_TYPE_RECEIVED -> (holder as ReceivedMessageViewHolder).bind(message)
            VIEW_TYPE_DATE_SEPARATOR -> (holder as DateSeparatorViewHolder).bind(message)
        }
    }

    // --- Modified addMessage to correctly handle date separators ---
    fun addMessage(message: MessagePublic) {
        val currentListMutable = currentList.toMutableList()
        currentListMutable.add(message)
        // Sort and add date separators to the entire list before submitting
        submitList(addDateSeparators(currentListMutable))
    }

    // --- CRITICAL FIX: Improved addDateSeparators logic ---
    private fun addDateSeparators(messages: List<MessagePublic>): List<MessagePublic> {
        if (messages.isEmpty()) return emptyList()

        // 1. Sort messages first by createdAt
        val sortedMessages = messages.sortedBy {
            try {
                LocalDateTime.parse(it.createdAt, BACKEND_DATETIME_FORMATTER)
            } catch (e: Exception) {
                // Fallback to min date if parsing fails, so messages with bad dates go to start
                Log.e("ChatAdapter", "Failed to parse message createdAt: ${it.createdAt}", e)
                LocalDateTime.MIN
            }
        }

        val messagesWithSeparators = mutableListOf<MessagePublic>()
        var lastDate: LocalDate? = null

        sortedMessages.forEach { message ->
            // Skip processing if it's already a date separator. We'll handle its placement and content.
            if (message.viewType == MessagePublic.VIEW_TYPE_DATE_SEPARATOR) {
                // We're about to add actual messages. If a separator has the same date as the next message,
                // it might be redundant. Let's just add it for now and let the subsequent logic handle `lastDate`.
                messagesWithSeparators.add(message)
                lastDate = try { LocalDateTime.parse(message.createdAt, BACKEND_DATETIME_FORMATTER).toLocalDate() } catch (e: Exception) { null }
                return@forEach
            }

            val messageDateTime = try {
                LocalDateTime.parse(message.createdAt, BACKEND_DATETIME_FORMATTER)
            } catch (e: Exception) {
                Log.e("ChatAdapter", "Failed to parse message date for separator logic: ${message.createdAt}", e)
                // If parsing fails, treat it as a new day to ensure a separator is added
                null
            }
            val messageDate = messageDateTime?.toLocalDate()

            // Only add a new separator if it's a new date AND it's not a duplicate separator
            if (messageDate != null && (lastDate == null || !messageDate.isEqual(lastDate))) {
                val separatorMessage = MessagePublic(
                    id = UUID.randomUUID().toString(),
                    conversationId = message.conversationId,
                    senderId = "", // No sender for separator
                    content = formatDateSeparator(messageDate), // Format the date
                    createdAt = message.createdAt, // Use message's time for sorting
                    viewType = MessagePublic.VIEW_TYPE_DATE_SEPARATOR
                )
                messagesWithSeparators.add(separatorMessage)
                lastDate = messageDate
            }
            messagesWithSeparators.add(message)
        }
        return messagesWithSeparators
    }

    // --- CRITICAL FIX: Improved formatDateSeparator logic for "TODAY"/"YESTERDAY" ---
    private fun formatDateSeparator(date: LocalDate): String {
        val nowInVietnam = ZonedDateTime.now(VIETNAM_ZONE_ID).toLocalDate() // Get today's date in VN timezone
        val yesterdayInVietnam = nowInVietnam.minusDays(1)
        val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale("vi", "VN"))

        return when {
            date.isEqual(nowInVietnam) -> "TODAY"
            date.isEqual(yesterdayInVietnam) -> "YESTERDAY"
            else -> date.format(dateFormatter)
        }
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

    // ViewHolder for Date Separator
    inner class DateSeparatorViewHolder(private val binding: ItemChatDateSeparatorBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(message: MessagePublic) {
            binding.tvDateSeparator.text = message.content
        }
    }


    class MessageDiffCallback : DiffUtil.ItemCallback<MessagePublic>() {
        override fun areItemsTheSame(oldItem: MessagePublic, newItem: MessagePublic): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MessagePublic, newItem: MessagePublic): Boolean {
            return oldItem == newItem
        }
    }

    private fun formatTime(utcDateTimeString: String): String {
        return try {
            val localDateTime = LocalDateTime.parse(utcDateTimeString, BACKEND_DATETIME_FORMATTER)
            val dateTimeInVietnam = localDateTime.atZone(ZoneId.of("UTC")).withZoneSameInstant(VIETNAM_ZONE_ID)
            dateTimeInVietnam.format(DateTimeFormatter.ofPattern("HH:mm", Locale("vi", "VN")))
        } catch (e: Exception) {
            Log.e("ChatAdapter", "Error parsing or formatting time: $utcDateTimeString - ${e.message}")
            "N/A"
        }
    }
}