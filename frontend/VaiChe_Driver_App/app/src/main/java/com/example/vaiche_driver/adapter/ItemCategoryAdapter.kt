package com.example.vaiche_driver.adapter

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
import com.example.vaiche_driver.model.CategoryPublic

class CategoryAdapter(
    private val onClick: (CategoryPublic) -> Unit
) : ListAdapter<CategoryPublic, CategoryAdapter.VH>(Diff()) {

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(item: CategoryPublic, onClick: (CategoryPublic) -> Unit) {
            // Best-effort binding theo id phổ biến
            val tvName = itemView.findViewById<TextView?>(R.id.tv_name)
            val ivIcon = itemView.findViewById<ImageView?>(R.id.iv_icon)

            tvName?.text = item.name
            ivIcon?.let {
                Glide.with(itemView).load(item.iconUrl)
                    .placeholder(R.drawable.ic_person_circle)
                    .into(it)
            }
            itemView.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_scrap_category, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position), onClick)
    }

    class Diff : DiffUtil.ItemCallback<CategoryPublic>() {
        override fun areItemsTheSame(oldItem: CategoryPublic, newItem: CategoryPublic) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: CategoryPublic, newItem: CategoryPublic) = oldItem == newItem
    }
}
