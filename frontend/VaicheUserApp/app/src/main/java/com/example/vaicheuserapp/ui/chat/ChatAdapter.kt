package com.example.vaicheuserapp.ui.chat

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.vaicheuserapp.R
import com.example.vaicheuserapp.data.model.MessagePublic
import com.example.vaicheuserapp.databinding.ItemChatDateSeparatorBinding
import com.example.vaicheuserapp.databinding.ItemChatMessageReceivedBinding
import com.example.vaicheuserapp.databinding.ItemChatMessageSentBinding
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

// Timezone & Date Formatter (can be moved to common place)
private val VIETNAM_ZONE_ID = ZoneId.of("Asia/Ho_Chi_Minh")
private val BACKEND_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")

class ChatAdapter(private val currentUserId: String) :
    ListAdapter<MessagePublic, RecyclerView.ViewHolder>(MessageDiffCallback()) {

    private val VIEW_TYPE_SENT = 1
    private val VIEW_TYPE_RECEIVED = 2
    private val VIEW_TYPE_DATE_SEPARATOR = 3

    override fun getItemViewType(position: Int): Int {
        val message = getItem(position)
        return when (message.viewType) { // Use the new viewType field
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
                DateSeparatorViewHolder(binding) // <-- NEW ViewHolder
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        when (holder.itemViewType) {
            VIEW_TYPE_SENT -> (holder as SentMessageViewHolder).bind(message)
            VIEW_TYPE_RECEIVED -> (holder as ReceivedMessageViewHolder).bind(message)
            VIEW_TYPE_DATE_SEPARATOR -> (holder as DateSeparatorViewHolder).bind(message) // <-- NEW Bind
        }
    }

    // Helper to add a single message (e.g., for optimistic update or new incoming message)
    fun addMessage(message: MessagePublic) {
        val currentListMutable = currentList.toMutableList()
        currentListMutable.add(message)
        // --- CRITICAL FIX: Re-process the entire list to add separators correctly ---
        submitList(addDateSeparators(currentListMutable))
    }

    private fun addDateSeparators(messages: List<MessagePublic>): List<MessagePublic> {
        if (messages.isEmpty()) return emptyList()

        val sortedMessages = messages.sortedBy { LocalDateTime.parse(it.createdAt, BACKEND_DATETIME_FORMATTER) }
        val messagesWithSeparators = mutableListOf<MessagePublic>()
        var lastDate: LocalDate? = null

        sortedMessages.forEach { message ->
            if (message.viewType == MessagePublic.VIEW_TYPE_DATE_SEPARATOR) {
                val separatorDate = try { LocalDateTime.parse(message.createdAt, BACKEND_DATETIME_FORMATTER).toLocalDate() } catch (e: Exception) { null }
                if (lastDate == null || separatorDate?.isAfter(lastDate) == true) {
                    messagesWithSeparators.add(message)
                    lastDate = separatorDate
                }
                return@forEach
            }

            val messageDateTime = LocalDateTime.parse(message.createdAt, BACKEND_DATETIME_FORMATTER)
            val messageDate = messageDateTime.toLocalDate()

            if (lastDate == null || !messageDate.isEqual(lastDate)) {
                val separatorMessage = MessagePublic(
                    id = UUID.randomUUID().toString(),
                    conversationId = message.conversationId,
                    senderId = "",
                    content = formatDateSeparator(messageDate),
                    createdAt = message.createdAt,
                    viewType = MessagePublic.VIEW_TYPE_DATE_SEPARATOR
                )
                messagesWithSeparators.add(separatorMessage)
                lastDate = messageDate
            }
            messagesWithSeparators.add(message)
        }
        return messagesWithSeparators
    }

    private fun formatDateSeparator(date: LocalDate): String {
        val now = LocalDate.now(VIETNAM_ZONE_ID)
        val yesterday = now.minusDays(1)
        val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale("vi", "VN"))

        return when {
            date.isEqual(now) -> "TODAY"
            date.isEqual(yesterday) -> "YESTERDAY"
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

    class MessageDiffCallback : DiffUtil.ItemCallback<MessagePublic>() {
        override fun areItemsTheSame(oldItem: MessagePublic, newItem: MessagePublic): Boolean {
            return oldItem.id == newItem.id
        }

        // --- CRITICAL FIX: Typo from previous step (areContentsAreTheSame) ---
        override fun areContentsTheSame(oldItem: MessagePublic, newItem: MessagePublic): Boolean {
            return oldItem == newItem
        }
    }

    inner class DateSeparatorViewHolder(private val binding: ItemChatDateSeparatorBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(message: MessagePublic) {
            binding.tvDateSeparator.text = message.content
        }
    }

    private fun formatTime(utcDateTimeString: String): String {
        return try {
            // 1. Parse the string into LocalDateTime using the custom formatter
            val localDateTime = LocalDateTime.parse(utcDateTimeString, BACKEND_DATETIME_FORMATTER)
            // 2. Treat this LocalDateTime as if it was in UTC, then convert to ZonedDateTime in Vietnam
            val dateTimeInVietnam = localDateTime.atZone(ZoneId.of("UTC")).withZoneSameInstant(VIETNAM_ZONE_ID)
            dateTimeInVietnam.format(DateTimeFormatter.ofPattern("HH:mm", Locale("vi", "VN"))) // e.g., "10:30"
        } catch (e: Exception) {
            Log.e("ChatAdapter", "Error parsing or formatting time: $utcDateTimeString - ${e.message}") // <-- Add logging
            "N/A"
        }
    }
}