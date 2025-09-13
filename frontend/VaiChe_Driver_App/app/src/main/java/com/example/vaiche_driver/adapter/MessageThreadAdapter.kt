package com.example.vaiche_driver.ui.messages

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.vaiche_driver.R
import com.example.vaiche_driver.model.MessagePublic
import com.example.vaiche_driver.viewmodel.ChatRow
import java.time.*
import java.time.format.DateTimeFormatter

private const val VT_SEPARATOR = 0
private const val VT_RECEIVED  = 1
private const val VT_SENT      = 2

class MessageThreadAdapter : ListAdapter<ChatRow, RecyclerView.ViewHolder>(Diff()) {

    class Diff : DiffUtil.ItemCallback<ChatRow>() {
        override fun areItemsTheSame(o: ChatRow, n: ChatRow): Boolean =
            when {
                o is ChatRow.DateSeparator && n is ChatRow.DateSeparator -> o.label == n.label
                o is ChatRow.Msg && n is ChatRow.Msg -> o.data.id == n.data.id
                else -> false
            }
        override fun areContentsTheSame(o: ChatRow, n: ChatRow) = o == n
    }

    override fun getItemViewType(position: Int): Int = when (val row = getItem(position)) {
        is ChatRow.DateSeparator -> VT_SEPARATOR
        is ChatRow.Msg -> if (row.isMine) VT_SENT else VT_RECEIVED
    }

    override fun onCreateViewHolder(p: ViewGroup, vt: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(p.context)
        return when (vt) {
            VT_SEPARATOR -> SepVH(inf.inflate(R.layout.item_chat_date_separator, p, false))
            VT_SENT      -> SentVH(inf.inflate(R.layout.item_chat_message_sent, p, false))
            else         -> RecvVH(inf.inflate(R.layout.item_chat_message_received, p, false))
        }
    }

    override fun onBindViewHolder(h: RecyclerView.ViewHolder, pos: Int) {
        when (h) {
            is SepVH  -> h.bind((getItem(pos) as ChatRow.DateSeparator).label)
            is SentVH -> h.bind((getItem(pos) as ChatRow.Msg).data)
            is RecvVH -> h.bind((getItem(pos) as ChatRow.Msg).data)
        }
    }

    class SepVH(v: View) : RecyclerView.ViewHolder(v) {
        private val tv = v.findViewById<TextView>(R.id.tv_date_separator)
        fun bind(label: String) { tv.text = label }
    }
    class SentVH(v: View) : RecyclerView.ViewHolder(v) {
        private val content = v.findViewById<TextView>(R.id.tv_message_content)
        private val time    = v.findViewById<TextView>(R.id.tv_message_time)
        fun bind(m: MessagePublic) {
            content.text = m.content
            time.text = formatTime(m.createdAt)     // <-- CHUẨN HOÁ
        }
    }
    class RecvVH(v: View) : RecyclerView.ViewHolder(v) {
        private val content = v.findViewById<TextView>(R.id.tv_message_content)
        private val time    = v.findViewById<TextView>(R.id.tv_message_time)
        fun bind(m: MessagePublic) {
            content.text = m.content
            time.text = formatTime(m.createdAt)     // <-- CHUẨN HOÁ
        }
    }

    companion object {
        private val OUT_TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

        // Parse linh hoạt nhiều dạng (ISO, ISO có micro giây…)
        private fun formatTime(raw: String): String {
            val instant = try {
                Instant.parse(raw) // ISO chuẩn: 2025-09-09T10:49:30Z
            } catch (_: Exception) {
                try {
                    OffsetDateTime.parse(raw).toInstant() // có offset
                } catch (_: Exception) {
                    try {
                        // backend kiểu "yyyy-MM-dd'T'HH:mm:ss.SSSSSS"
                        val ldt = LocalDateTime.parse(
                            raw.substring(0, minOf(raw.length, 26)), // cắt bớt nếu > 26
                            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSSSSS]")
                        )
                        ldt.atZone(ZoneOffset.UTC).toInstant()
                    } catch (_: Exception) {
                        Instant.now()
                    }
                }
            }
            val local = instant.atZone(ZoneId.systemDefault())
            return OUT_TIME_FMT.format(local)
        }
    }
}
