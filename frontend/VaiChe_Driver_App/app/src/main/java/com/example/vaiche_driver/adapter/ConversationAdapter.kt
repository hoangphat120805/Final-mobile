package com.example.vaiche_driver.ui.messages

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.vaiche_driver.R
import com.example.vaiche_driver.viewmodel.ConversationRow

class ConversationsAdapter(
    private val onClick: (ConversationRow) -> Unit
) : ListAdapter<ConversationRow, ConversationsAdapter.VH>(Diff()) {

    class Diff : DiffUtil.ItemCallback<ConversationRow>() {
        override fun areItemsTheSame(o: ConversationRow, n: ConversationRow) = o.id == n.id
        override fun areContentsTheSame(o: ConversationRow, n: ConversationRow) = o == n
    }

    init {
        // Ổn định item khi cập nhật liên tục
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).id.hashCode().toLong()

    override fun onCreateViewHolder(p: ViewGroup, vt: Int): VH {
        val v = LayoutInflater.from(p.context).inflate(R.layout.item_conversation, p, false)
        return VH(v, onClick)
    }

    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(getItem(pos))

    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        // Ngăn ảnh cũ “dính” khi ViewHolder được tái sử dụng
        Glide.with(holder.itemView).clear(holder.avatar)
        holder.avatar.setImageResource(R.drawable.ic_person_circle)
    }

    class VH(v: View, val onClick: (ConversationRow) -> Unit) : RecyclerView.ViewHolder(v) {
        val avatar: ImageView = v.findViewById(R.id.iv_conversation_avatar)
        private val name: TextView = v.findViewById(R.id.tv_conversation_name)
        private val preview: TextView = v.findViewById(R.id.tv_last_message_content)
        private val time: TextView = v.findViewById(R.id.tv_last_message_time)
        private var bound: ConversationRow? = null

        init { v.setOnClickListener { bound?.let(onClick) } }

        fun bind(row: ConversationRow) {
            bound = row

            name.text = row.name
            preview.text = row.lastMessagePreview
            time.text = row.lastTimeLabel

            // Đặt placeholder trước, sau đó mới load/clear tuỳ theo url
            avatar.setImageResource(R.drawable.ic_person_circle)

            val url = row.avatarUrl
            if (url.isNullOrBlank()) {
                // Clear mọi request cũ gắn với ImageView để tránh hiển thị ảnh sai
                Glide.with(itemView).clear(avatar)
                avatar.setImageResource(R.drawable.ic_person_circle)
            } else {
                Glide.with(itemView)
                    .load(url)
                    .placeholder(R.drawable.ic_person_circle)
                    .error(R.drawable.ic_person_circle)
                    .circleCrop()
                    .into(avatar)
            }
        }
    }
}
