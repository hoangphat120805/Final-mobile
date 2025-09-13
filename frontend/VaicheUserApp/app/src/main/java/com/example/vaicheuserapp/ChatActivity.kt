package com.example.vaicheuserapp

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import coil.transform.CircleCropTransformation
import com.example.vaicheuserapp.data.model.ConversationWithLastMessage
import com.example.vaicheuserapp.data.model.MessageCreate
import com.example.vaicheuserapp.data.model.MessagePublic
import com.example.vaicheuserapp.data.model.UserPublic
import com.example.vaicheuserapp.data.network.RetrofitClient
import com.example.vaicheuserapp.databinding.ActivityChatBinding
import com.example.vaicheuserapp.ui.chat.ChatAdapter
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.collections.Map
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var currentConversation: ConversationWithLastMessage
    private lateinit var currentUserId: String

    // --- CRITICAL FIX: Keep a map of all participants for avatar/name lookup ---
    private val chatParticipants = mutableMapOf<String, UserPublic>() // userId -> UserPublic

    private var webSocket: WebSocket? = null
    private val okHttpClient = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()
    private val gson = Gson()

    private val VIETNAM_ZONE_ID = ZoneId.of("Asia/Ho_Chi_Minh")
    private val BACKEND_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentConversation = intent.getParcelableExtra("EXTRA_CONVERSATION")
            ?: run {
                Toast.makeText(this, "Conversation data missing.", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

        currentUserId = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getString("user_id", "") ?: ""

        if (currentUserId.isEmpty()) {
            Toast.makeText(this, "User ID not found.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Add current user to participants map (for their own avatar/name lookup)
        // This requires fetching current user's full details (e.g., from ProfileFragment cache or API)
        // For now, let's assume current user's profile is available or fetched.
        fetchCurrentUserProfile() // Will populate chatParticipants with current user

        setupToolbar()
        setupRecyclerView()
        setupListeners()

        // --- Fetch all participants first, then messages ---
        fetchConversationParticipants()
    }

    private fun setupToolbar() {
        binding.ivBackButton.setOnClickListener { finish() }
        // Chat partner name and avatar updated by fetchChatPartnerDetails
        binding.tvToolbarTitle.text = currentConversation.name ?: "Chat" // Default until partner is loaded
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(currentUserId, chatParticipants) // Pass the participants map
        binding.rvChatMessages.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity)
            adapter = chatAdapter
            // --- CRITICAL FIX: Add a listener for keyboard to scroll to bottom ---
            // This listener ensures that when layout changes (e.g., keyboard pops up),
            // it scrolls to the last message.
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                if (chatAdapter.itemCount > 0) {
                    binding.rvChatMessages.post { // Post to ensure scroll happens after layout
                        (binding.rvChatMessages.layoutManager as? LinearLayoutManager)?.let {
                            if (it.findLastVisibleItemPosition() != chatAdapter.itemCount - 1) { // Only scroll if not already at bottom
                                binding.rvChatMessages.scrollToPosition(chatAdapter.itemCount - 1)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupListeners() {
        binding.btnSendMessage.setOnClickListener { sendMessage() }
    }

    // --- NEW: Fetch current user profile to add to participants map ---
    private fun fetchCurrentUserProfile() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getUserMe()
                if (response.isSuccessful && response.body() != null) {
                    chatParticipants[currentUserId] = response.body()!!
                } else {
                    Log.e("ChatActivity", "Failed to fetch current user profile: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("ChatActivity", "Error fetching current user profile: ${e.message}", e)
            }
        }
    }

    // --- Fetch chat partner details for toolbar ---
    private fun fetchConversationParticipants() {
        lifecycleScope.launch {
            try {
                // Extract all unique user IDs from the current conversation object
                val allUserIds = currentConversation.members // <-- CRITICAL: Access 'members'
                    ?.map { it.userId } // <-- Extract userId from ConversationMember
                    ?.distinct() ?: emptyList()

                val otherUserIds = allUserIds.filter { it != currentUserId }

                val deferredUserFetches = (otherUserIds + currentUserId).distinct().map { userId ->
                    async(Dispatchers.IO) { // Using async here
                        if (chatParticipants.containsKey(userId)) {
                            chatParticipants[userId] // Use cached if available
                        } else {
                            val response = RetrofitClient.instance.getUser(userId)
                            if (response.isSuccessful && response.body() != null) {
                                val user = response.body()!!
                                chatParticipants[userId] = user // Cache it
                                user
                            } else {
                                Log.e("ChatActivity", "Failed to fetch user $userId: ${response.code()}")
                                null
                            }
                        }
                    }
                }
                deferredUserFetches.awaitAll()

                fetchChatPartnerDetails()
                fetchMessages()
                connectWebSocket()
            } catch (e: Exception) {
                Log.e("ChatActivity", "Error fetching conversation participants: ${e.message}", e)
                fetchChatPartnerDetails()
                fetchMessages()
                connectWebSocket()
            }
        }
    }

    // --- CRITICAL FIX: Fetch chat partner details for toolbar (using .members) ---
    private fun fetchChatPartnerDetails() {
        val otherMemberId = currentConversation.members // <-- CRITICAL: Access 'members'
            ?.map { it.userId } // <-- Extract userId
            ?.firstOrNull { it != currentUserId }

        if (otherMemberId != null) {
            val partner = chatParticipants[otherMemberId]
            binding.tvToolbarTitle.text = partner?.fullName ?: currentConversation.name ?: "Chat Partner"
            binding.ivChatToolbarAvatar.load(partner?.avtUrl, RetrofitClient.imageLoader) {
                crossfade(true)
                transformations(CircleCropTransformation())
                placeholder(R.drawable.default_avatar)
                error(R.drawable.bg_image_error)
            }
        } else {
            binding.tvToolbarTitle.text = currentConversation.name ?: "Group Chat"
            binding.ivChatToolbarAvatar.setImageResource(R.drawable.default_avatar)
        }
    }


    private fun fetchMessages() {
        binding.pbLoadingMessages.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getMessages(currentConversation.id)
                if (response.isSuccessful && response.body() != null) {
                    val messages = response.body()!!
                    val messagesWithSeparators = addDateSeparators(messages)
                    chatAdapter.submitList(messagesWithSeparators) {
                        binding.rvChatMessages.scrollToPosition(chatAdapter.itemCount - 1)
                    }
                } else {
                    Log.e("ChatActivity", "Failed to load messages: ${response.code()} - ${response.errorBody()?.string()}")
                    Toast.makeText(this@ChatActivity, "Failed to load messages.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("ChatActivity", "Error fetching messages: ${e.message}", e)
                Toast.makeText(this@ChatActivity, "Error loading messages.", Toast.LENGTH_SHORT).show()
            } finally {
                binding.pbLoadingMessages.visibility = View.GONE
            }
        }
    }

    private fun addDateSeparators(messages: List<MessagePublic>): List<MessagePublic> {
        if (messages.isEmpty()) return emptyList()

        val sortedMessages = messages.sortedBy {
            try {
                LocalDateTime.parse(it.createdAt, BACKEND_DATETIME_FORMATTER)
            } catch (e: Exception) {
                Log.e("ChatActivity", "Failed to parse message createdAt for sorting: ${it.createdAt}", e)
                LocalDateTime.MIN // Default to min date to put invalid dates at start
            }
        }

        val messagesWithSeparators = mutableListOf<MessagePublic>()
        var lastDate: LocalDate? = null

        sortedMessages.forEach { message ->
            // Skip processing if it's already a date separator. We'll ensure it's not duplicated.
            if (message.viewType == MessagePublic.VIEW_TYPE_DATE_SEPARATOR) {
                // If the last message added was not a separator, and this separator is new, add it.
                // Otherwise, it might be a duplicate being re-added by submitList.
                val separatorDate = try { LocalDateTime.parse(message.createdAt, BACKEND_DATETIME_FORMATTER).toLocalDate() } catch (e: Exception) { null }
                if (messagesWithSeparators.lastOrNull()?.viewType != MessagePublic.VIEW_TYPE_DATE_SEPARATOR ||
                    (separatorDate != null && lastDate != null && separatorDate.isAfter(lastDate))) {
                    messagesWithSeparators.add(message)
                    lastDate = separatorDate
                }
                return@forEach
            }

            val messageDateTime = try {
                LocalDateTime.parse(message.createdAt, BACKEND_DATETIME_FORMATTER)
            } catch (e: Exception) {
                Log.e("ChatActivity", "Failed to parse message date for separator logic: ${message.createdAt}", e)
                null // Treat as new day if parsing fails, to ensure a separator is added
            }
            val messageDate = messageDateTime?.toLocalDate()

            if (messageDate != null && (lastDate == null || !messageDate.isEqual(lastDate))) {
                val separatorMessage = MessagePublic(
                    id = UUID.randomUUID().toString(),
                    conversationId = message.conversationId,
                    senderId = "",
                    content = formatDateSeparator(messageDate),
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

    private fun connectWebSocket() {
        val token = getSharedPreferences("app_prefs", Context.MODE_PRIVATE).getString("auth_token", null)
        if (token.isNullOrEmpty()) {
            Toast.makeText(this, "Authentication token missing for chat.", Toast.LENGTH_SHORT).show()
            return
        }

        // --- CRITICAL: WebSocket URL for chat (from your backend spec) ---
        val wsUrl = "ws://160.30.192.11:8000/api/chat/ws/chat" // This is the generic chat websocket
        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("Authorization", "Bearer $token")
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                Log.d("ChatWebSocket", "WebSocket Opened: ${response.message}")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("ChatWebSocket", "Received text: $text")
                try {
                    val jsonMap = gson.fromJson(text, Map::class.java) as Map<String, Any?>
                    val type = jsonMap["type"] as? String

                    if (type == "message") {
                        val data = jsonMap["data"] as? Map<String, Any?>
                        data?.let {
                            val id = it["id"] as? String ?: UUID.randomUUID().toString()
                            val convId = it["conversation_id"] as? String ?: currentConversation.id
                            val senderId = it["sender_id"] as? String ?: UUID.randomUUID().toString()
                            val content = it["content"] as? String ?: ""
                            val createdAt = it["created_at"] as? String ?: LocalDateTime.now(ZoneId.of("UTC")).format(BACKEND_DATETIME_FORMATTER)

                            val receivedMessage = MessagePublic(id, convId, senderId, content, createdAt)

                            if (receivedMessage.conversationId == currentConversation.id) {
                                runOnUiThread {
                                    chatAdapter.addMessage(receivedMessage)
                                    binding.rvChatMessages.scrollToPosition(chatAdapter.itemCount - 1)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ChatWebSocket", "Error parsing incoming message: ${e.message}", e)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("ChatWebSocket", "WebSocket Closing: $code / $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                Log.e("ChatWebSocket", "WebSocket Failure: ${t.message}", t)
                runOnUiThread {
                    Toast.makeText(this@ChatActivity, "Chat disconnected: ${t.message}", Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    private fun sendMessage() {
        val messageContent = binding.etMessageInput.text.toString().trim()
        if (messageContent.isEmpty()) return

        val messageCreate = MessageCreate(currentConversation.id, messageContent)
        val messageJson = gson.toJson(mapOf("type" to "message", "data" to messageCreate))

        webSocket?.send(messageJson)

        val tempMessageId = UUID.randomUUID().toString()
        val currentTimeFormatted = LocalDateTime.now(ZoneId.of("UTC")).format(BACKEND_DATETIME_FORMATTER)
        val tempMessage = MessagePublic(tempMessageId, currentConversation.id, currentUserId, messageContent, currentTimeFormatted)

        // Add message to adapter
        // Then re-process the list to add separators correctly
        val updatedList = (chatAdapter.currentList + tempMessage).toMutableList()
        val messagesWithSeparators = addDateSeparators(updatedList)
        chatAdapter.submitList(messagesWithSeparators) {
            // --- CRITICAL FIX: Scroll after submitList is done updating UI ---
            binding.rvChatMessages.scrollToPosition(chatAdapter.itemCount - 1)
        }

        binding.etMessageInput.text.clear()
        hideKeyboard()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { view ->
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocket?.close(1000, "Activity Destroyed") // Close WebSocket gracefully
        okHttpClient.dispatcher.cancelAll()
    }
}