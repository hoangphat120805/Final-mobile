package com.example.vaicheuserapp.ui.dashboard

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.vaicheuserapp.R
import com.example.vaicheuserapp.data.model.CategoryPublic
import com.example.vaicheuserapp.data.network.RetrofitClient
import com.example.vaicheuserapp.databinding.ItemScrapCategoryBinding
import java.text.NumberFormat
import java.util.Locale

// 1. Extend ListAdapter and pass the click listener in the constructor
class CategoryAdapter(
    private val onItemClick: (CategoryPublic) -> Unit
) : ListAdapter<CategoryPublic, CategoryAdapter.CategoryViewHolder>(CategoryDiffCallback()) {

    // 2. The ViewHolder is the same, but we add the click listener logic inside it
    inner class CategoryViewHolder(val binding: ItemScrapCategoryBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                // Check for a valid position before calling the click listener
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(adapterPosition))
                }
            }
        }

        fun bind(category: CategoryPublic) {
            with(binding) {
                tvCategoryName.text = category.name

                val priceText = category.price?.let {
                    val format = NumberFormat.getCurrencyInstance(Locale("vi", "VN"))
                    "${format.format(it)}/${category.unit}"
                } ?: "N/A"
                tvCategoryPrice.text = priceText

                ivCategoryIcon.load(category.iconUrl, RetrofitClient.imageLoader) {
                    crossfade(true)
                    placeholder(R.drawable.bg_image_placeholder)
                    error(R.drawable.bg_image_placeholder) // Use the same placeholder for error state
                    listener(
                        onError = { _, result ->
                            Log.e("CoilDebug", "ERROR loading image: ${category.iconUrl}")
                            result.throwable.printStackTrace()
                        }
                    )
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemScrapCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = getItem(position) // 3. Use getItem() from ListAdapter
        holder.bind(category)
    }

    // 4. We need this DiffUtil.ItemCallback for ListAdapter to work
    class CategoryDiffCallback : DiffUtil.ItemCallback<CategoryPublic>() {
        override fun areItemsTheSame(oldItem: CategoryPublic, newItem: CategoryPublic): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CategoryPublic, newItem: CategoryPublic): Boolean {
            return oldItem == newItem
        }
    }

    // 5. DELETE the old updateData function and getItemCount(). ListAdapter handles this automatically.
}