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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

// Timezone & Date Formatter (can be moved to common place)
private val VIETNAM_ZONE_ID = ZoneId.of("Asia/Ho_Chi_Minh")
private val BACKEND_DATETIME_FORMATTER_WITH_ZONE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'")
private val BACKEND_DATETIME_FORMATTER_NO_ZONE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")

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

    private fun addDateSeparators(messages: List<MessagePublic>): List<MessagePublic> {
        if (messages.isEmpty()) return emptyList()

        // Helper to parse any message createdAt string into a ZonedDateTime in Vietnam time
        fun parseAndConvertToVietnamTime(dateTimeString: String): ZonedDateTime? {
            return try {
                if (dateTimeString.endsWith("Z")) { // If it has 'Z' (from optimistic update)
                    Instant.parse(dateTimeString).atZone(VIETNAM_ZONE_ID)
                } else { // If no 'Z' (from backend)
                    LocalDateTime.parse(dateTimeString, BACKEND_DATETIME_FORMATTER_NO_ZONE)
                        .atZone(ZoneId.of("UTC")).withZoneSameInstant(VIETNAM_ZONE_ID)
                }
            } catch (e: Exception) {
                Log.e("ChatAdapter", "Failed to parse/convert date for separator logic: $dateTimeString", e)
                null
            }
        }

        val sortedMessages = messages.mapNotNull { msg ->
            parseAndConvertToVietnamTime(msg.createdAt)?.let { Pair(msg, it) }
        }.sortedBy { it.second }

        val finalMessagesWithSeparators = mutableListOf<MessagePublic>()
        var lastDateProcessed: LocalDate? = null

        sortedMessages.forEach { (message, messageVietnamZonedDateTime) ->
            if (message.viewType == MessagePublic.VIEW_TYPE_DATE_SEPARATOR) {
                val separatorDate = try { LocalDateTime.parse(message.createdAt, BACKEND_DATETIME_FORMATTER_NO_ZONE).toLocalDate() } catch (e: Exception) { null } // Use NO_ZONE for existing separator dates if no Z
                if (separatorDate != null) {
                    if (lastDateProcessed == null || !separatorDate.isEqual(lastDateProcessed)) {
                        finalMessagesWithSeparators.add(message)
                        lastDateProcessed = separatorDate
                    }
                }
                return@forEach
            }

            val messageDateInVietnam = messageVietnamZonedDateTime.toLocalDate()

            if (messageDateInVietnam != null && (lastDateProcessed == null || !messageDateInVietnam.isEqual(lastDateProcessed))) {
                val separatorMessage = MessagePublic(
                    id = UUID.randomUUID().toString(),
                    conversationId = message.conversationId,
                    senderId = "",
                    content = formatDateSeparator(messageDateInVietnam),
                    createdAt = message.createdAt, // Original createdAt (UTC or VN formatted)
                    viewType = MessagePublic.VIEW_TYPE_DATE_SEPARATOR
                )
                finalMessagesWithSeparators.add(separatorMessage)
                lastDateProcessed = messageDateInVietnam
            }
            finalMessagesWithSeparators.add(message)
        }
        return finalMessagesWithSeparators
    }

    private fun formatDateSeparator(date: LocalDate): String {
        val nowInVietnam = ZonedDateTime.now(VIETNAM_ZONE_ID).toLocalDate()
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

    private fun formatTime(dateTimeString: String): String { // Renamed param for clarity
        return try {
            // Helper to parse any message createdAt string into a ZonedDateTime in Vietnam time
            fun parseAndConvertToVietnamTime(dateTimeString: String): ZonedDateTime? {
                return try {
                    if (dateTimeString.endsWith("Z")) {
                        Instant.parse(dateTimeString).atZone(VIETNAM_ZONE_ID)
                    } else {
                        LocalDateTime.parse(dateTimeString, BACKEND_DATETIME_FORMATTER_NO_ZONE)
                            .atZone(ZoneId.of("UTC")).withZoneSameInstant(VIETNAM_ZONE_ID)
                    }
                } catch (e: Exception) {
                    Log.e("ChatAdapter", "Failed to parse/convert time for display: $dateTimeString", e)
                    null
                }
            }

            val dateTimeInVietnam = parseAndConvertToVietnamTime(dateTimeString)
            dateTimeInVietnam?.format(DateTimeFormatter.ofPattern("HH:mm", Locale("vi", "VN"))) ?: "N/A"

        } catch (e: Exception) {
            Log.e("ChatAdapter", "Critical error in formatTime final step: ${e.message}", e)
            "N/A"
        }
    }
}