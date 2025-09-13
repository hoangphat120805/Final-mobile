package com.example.vaicheuserapp.data.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize
import java.time.LocalDateTime // Required for createdAt/timestamp parsing

// For /api/chat/conversations/{conversation_id}/messages/ -> MessagePublic
@Parcelize
data class MessagePublic(
    val id: String,
    @SerializedName("conversation_id") val conversationId: String,
    @SerializedName("sender_id") val senderId: String,
    val content: String,
    @SerializedName("created_at") val createdAt: String, // date-time string
    val viewType: Int = VIEW_TYPE_MESSAGE
) : Parcelable {
    companion object {
        const val VIEW_TYPE_MESSAGE = 0 // Regular message
        const val VIEW_TYPE_DATE_SEPARATOR = 1 // <-- NEW: For date separators
    }
}

// For websocket message sending
data class MessageCreate(
    @SerializedName("conversation_id") val conversationId: String,
    val content: String
)

// For /api/chat/conversations/ (request body)
data class ConversationCreate(
    val name: String?, // Optional
    val type: ConversationType = ConversationType.PRIVATE,
    @SerializedName("member_ids") val memberIds: List<String> // List of user IDs (UUID strings)
)

// For /api/chat/conversations/ (response)
@Parcelize
data class ConversationPublic(
    val id: String,
    val name: String?,
    val type: ConversationType,
    @SerializedName("last_message_id") val lastMessageId: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String
) : Parcelable

// For /api/chat/conversations/ (response with last message included)
@Parcelize
data class ConversationMember(
    @SerializedName("user_id") val userId: String
) : Parcelable

@Parcelize
data class ConversationWithLastMessage(
    val id: String,
    val name: String?,
    val type: ConversationType,
    @SerializedName("members") val members: List<ConversationMember>?, // <-- CRITICAL: ADD THIS FIELD
    @SerializedName("last_message_id") val lastMessageId: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("last_message") val lastMessage: MessagePublic?
) : Parcelable
enum class ConversationType {
    @SerializedName("private") PRIVATE,
    @SerializedName("group") GROUP
}