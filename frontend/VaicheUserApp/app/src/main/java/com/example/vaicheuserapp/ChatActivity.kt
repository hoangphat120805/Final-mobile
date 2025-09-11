package com.example.vaicheuserapp

import android.content.Context
import android.os.Build
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
import com.example.vaicheuserapp.data.model.ConversationWithLastMessage
import com.example.vaicheuserapp.data.model.MessageCreate
import com.example.vaicheuserapp.data.model.MessagePublic
import com.example.vaicheuserapp.data.network.RetrofitClient
import com.example.vaicheuserapp.databinding.ActivityChatBinding
import com.example.vaicheuserapp.ui.chat.ChatAdapter // <-- NEW IMPORT
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var currentConversation: ConversationWithLastMessage
    private lateinit var currentUserId: String

    private var webSocket: WebSocket? = null
    private val okHttpClient = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()
    private val gson = Gson()

    // Timezone & Date Formatter (can be moved to common place)
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

        setupToolbar()
        setupRecyclerView()
        setupListeners()
        fetchMessages()
        connectWebSocket()
    }

    private fun setupToolbar() {
        binding.ivBackButton.setOnClickListener { finish() }
        binding.tvToolbarTitle.text = currentConversation.name ?: "Chat"
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(currentUserId)
        binding.rvChatMessages.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity)
            adapter = chatAdapter
        }
    }

    private fun setupListeners() {
        binding.btnSendMessage.setOnClickListener { sendMessage() }
        binding.etMessageInput.setOnClickListener {
            // Scroll to bottom when input is focused/keyboard appears
            binding.rvChatMessages.scrollToPosition(chatAdapter.itemCount - 1)
        }
    }

    private fun fetchMessages() {
        binding.pbLoadingMessages.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getMessages(currentConversation.id)
                if (response.isSuccessful && response.body() != null) {
                    val messages = response.body()!!
                    chatAdapter.submitList(messages)
                    binding.rvChatMessages.scrollToPosition(messages.size - 1) // Scroll to latest
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

    private fun connectWebSocket() {
        val token = getSharedPreferences("app_prefs", Context.MODE_PRIVATE).getString("auth_token", null)
        if (token.isNullOrEmpty()) {
            Toast.makeText(this, "Authentication token missing for chat.", Toast.LENGTH_SHORT).show()
            return
        }

        // --- CRITICAL: WebSocket URL for chat (from your backend spec) ---
        val wsUrl = "ws://160.30.192.11:8000/ws/chat" // This is the generic chat websocket
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
                            val createdAt = it["created_at"] as? String ?: LocalDateTime.now(VIETNAM_ZONE_ID).format(DateTimeFormatter.ISO_DATE_TIME)

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

        webSocket?.send(messageJson) // Send via WebSocket

        // Optimistically add message to UI
        val tempMessageId = UUID.randomUUID().toString() // Temporary ID
        val tempMessage = MessagePublic(tempMessageId, currentConversation.id, currentUserId, messageContent, LocalDateTime.now(VIETNAM_ZONE_ID).format(DateTimeFormatter.ISO_DATE_TIME))
        chatAdapter.addMessage(tempMessage)
        binding.rvChatMessages.scrollToPosition(chatAdapter.itemCount - 1)

        binding.etMessageInput.text.clear() // Clear input
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