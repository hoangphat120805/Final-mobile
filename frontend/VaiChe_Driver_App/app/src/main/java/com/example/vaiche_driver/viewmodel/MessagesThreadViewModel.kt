package com.example.vaiche_driver.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vaiche_driver.data.network.RetrofitClient
import com.example.vaiche_driver.data.repository.MessageRepository
import com.example.vaiche_driver.model.MessagePublic
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

sealed class ChatRow {
    data class DateSeparator(val label: String) : ChatRow()
    data class Msg(val data: MessagePublic, val isMine: Boolean) : ChatRow()
}

class MessagesThreadViewModel(
    private val repo: MessageRepository = MessageRepository { RetrofitClient.instance }
) : ViewModel() {

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _rows = MutableLiveData<List<ChatRow>>(emptyList())
    val rows: LiveData<List<ChatRow>> = _rows

    private val _toast = MutableLiveData<Event<String>>()
    val toast: LiveData<Event<String>> = _toast

    // để xác định "isMine" khi nhận WS
    private var currentConversationId: String? = null
    private var myUserId: String? = null

    // WebSocket
    private var ws: WebSocket? = null
    private var wsClient: OkHttpClient? = null

    // ================= API: load messages =================
    fun load(conversationId: String, myId: String) {
        currentConversationId = conversationId
        myUserId = myId
        _isLoading.value = true
        viewModelScope.launch {
            repo.getMessages(conversationId)
                .onSuccess { list ->
                    val sorted = list.sortedBy { it.createdAt } // cũ -> mới
                    _rows.value = buildRows(sorted, myId)
                }
                .onFailure { e ->
                    _toast.postValue(Event("Load messages failed: ${e.message}"))
                }
            _isLoading.value = false
        }
    }

    private fun buildRows(src: List<MessagePublic>, myUserId: String): List<ChatRow> {
        val out = mutableListOf<ChatRow>()
        var lastDate: LocalDate? = null
        src.forEach { m ->
            val d = safeParse(m.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
            if (d != lastDate) {
                lastDate = d
                out += ChatRow.DateSeparator(formatDateLabel(d))
            }
            out += ChatRow.Msg(m, isMine = (m.senderId == myUserId))
        }
        return out
    }

    private fun formatDateLabel(d: LocalDate): String {
        val today = LocalDate.now()
        return when (d) {
            today -> "TODAY"
            today.minusDays(1) -> "YESTERDAY"
            else -> d.toString()
        }
    }

    private fun safeParse(s: String): Instant =
        try { Instant.parse(s) }
        catch (_: Exception) {
            try { OffsetDateTime.parse(s).toInstant() }
            catch (_: Exception) { Instant.now() }
        }

    // ======= FIX LẶP TODAY: thêm tin theo ngày thực sự của message cuối, không nhìn row cuối là gì =======
    private fun appendMessageSmart(m: MessagePublic, isMine: Boolean) {
        val cur = _rows.value.orEmpty().toMutableList()

        val newDate = safeParse(m.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
        val lastMsgDate = findLastMessageDate(cur)

        if (lastMsgDate == null || lastMsgDate != newDate) {
            cur += ChatRow.DateSeparator(formatDateLabel(newDate))
        }
        cur += ChatRow.Msg(m, isMine)
        _rows.postValue(cur)
    }

    private fun findLastMessageDate(rows: List<ChatRow>): LocalDate? {
        for (i in rows.size - 1 downTo 0) {
            val r = rows[i]
            if (r is ChatRow.Msg) {
                return safeParse(r.data.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
            }
        }
        return null
    }

    /** Append local (optimistic) khi gửi tin – hiển thị ngay, KHÔNG lặp TODAY nữa */
    fun appendLocal(conversationId: String, myUserId: String, text: String) {
        val fake = MessagePublic(
            conversationId = conversationId,
            content = text,
            id = "client-" + System.currentTimeMillis(),
            senderId = myUserId,
            createdAt = Instant.now().toString()
        )
        appendMessageSmart(fake, isMine = true)
    }

    // ================= WebSocket =================
    /**
     * baseHttpUrl ví dụ: "http://160.30.192.11:8000"
     * token là JWT không có tiền tố "Bearer "
     */
    fun connectWs(baseHttpUrl: String, token: String) {
        closeWs()

        val wsBase = if (baseHttpUrl.startsWith("https"))
            baseHttpUrl.replaceFirst("https", "wss")
        else
            baseHttpUrl.replaceFirst("http", "ws")

        val url = "$wsBase/api/chat/ws/chat?token=$token" // query token

        wsClient = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token") // header token
            .build()

        ws = wsClient!!.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WS", "opened ${response.code}")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val obj = JSONObject(text)
                    if (obj.optString("type") == "message") {
                        val data = obj.getJSONObject("data")
                        val msg = MessagePublic(
                            id = data.getString("id"),
                            conversationId = data.getString("conversation_id"),
                            senderId = data.getString("sender_id"),
                            content = data.getString("content"),
                            createdAt = data.getString("created_at")
                        )
                        // chỉ append nếu đúng phòng hiện tại
                        if (msg.conversationId == currentConversationId) {
                            val mine = msg.senderId == myUserId
                            appendMessageSmart(msg, mine)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("WS", "parse error: ${e.message}")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                _toast.postValue(Event("WS closing: $code $reason"))
            }

            override fun onFailure(
                webSocket: WebSocket,
                t: Throwable,
                response: Response?
            ) {
                val code = response?.code
                val msg = response?.message
                _toast.postValue(Event("WS failed: $code $msg - ${t.message}"))
            }
        })
    }

    fun sendViaWs(conversationId: String, content: String) {
        val payload = """{
          "type":"message",
          "data":{"conversation_id":"$conversationId","content":${JSONObject.quote(content)}}
        }""".trimIndent()
        val ok = ws?.send(payload) ?: false
        if (!ok) _toast.postValue(Event("WS not connected"))
    }

    fun closeWs() {
        try { ws?.close(1000, "bye") } catch (_: Exception) {}
        ws = null
        try { wsClient?.dispatcher?.executorService?.shutdown() } catch (_: Exception) {}
        wsClient = null
    }

    override fun onCleared() {
        super.onCleared()
        closeWs()
    }
}
