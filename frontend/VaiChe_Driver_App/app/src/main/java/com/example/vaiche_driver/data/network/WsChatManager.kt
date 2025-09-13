// WsChatManager.kt
package com.example.vaiche_driver.data.network

import com.google.gson.Gson
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import com.example.vaiche_driver.model.MessagePublic

class WsChatManager(
    private val baseWsUrl: String = "ws://160.30.192.11:8000/ws/chat"
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // WS giữ kết nối lâu
        .build()

    private val gson = Gson()
    private var webSocket: WebSocket? = null

    fun connect(
        token: String,
        useHeaderAuth: Boolean = true, // nếu backend đọc token từ header
        onOpen: () -> Unit = {},
        onIncomingMessage: (MessagePublic) -> Unit = {},
        onClosed: (code: Int, reason: String?) -> Unit = { _, _ -> },
        onFailure: (Throwable) -> Unit = {}
    ) {
        val url = if (useHeaderAuth) baseWsUrl else "$baseWsUrl?token=$token"
        val reqBuilder = Request.Builder().url(url)
        if (useHeaderAuth) reqBuilder.addHeader("Authorization", "Bearer $token")

        val request = reqBuilder.build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                onOpen()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val root = JSONObject(text)
                    if (root.optString("type") == "message") {
                        val data = root.getJSONObject("data").toString()
                        val msg = gson.fromJson(data, MessagePublic::class.java)
                        onIncomingMessage(msg)
                    }
                } catch (_: Exception) { /* ignore parse error */ }
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                // nếu server có gửi binary (hiện tại không)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(1000, null)
                onClosed(code, reason)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                onFailure(t)
            }
        })
    }

    fun sendMessage(conversationId: String, content: String): Boolean {
        val payload = mapOf(
            "type" to "message",
            "data" to mapOf(
                "conversation_id" to conversationId,
                "content" to content
            )
        )
        val json = Gson().toJson(payload)
        return webSocket?.send(json) ?: false
    }

    fun close() {
        try { webSocket?.close(1000, "bye") } catch (_: Exception) {}
        webSocket = null
    }
}
