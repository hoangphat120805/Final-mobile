package com.example.vaicheuserapp.ui.dashboard

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.vaicheuserapp.R
import com.example.vaicheuserapp.data.model.CategoryPublic
import com.example.vaicheuserapp.databinding.ItemScrapCategoryBinding
import java.text.NumberFormat
import java.util.Locale
import com.example.vaicheuserapp.data.network.RetrofitClient
import android.util.Log

class CategoryAdapter(private var categories: List<CategoryPublic>) :
    RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    inner class CategoryViewHolder(val binding: ItemScrapCategoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemScrapCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CategoryViewHolder(binding)
    }

    override fun getItemCount() = categories.size

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categories[position]
        with(holder.binding) {
            tvCategoryName.text = category.name

            // Format the price nicely
            val priceText = category.price?.let {
                val format = NumberFormat.getCurrencyInstance(Locale("vi", "VN"))
                "${format.format(it)}/${category.unit}"
            } ?: "N/A"
            tvCategoryPrice.text = priceText

            // Load image with Coil
            ivCategoryIcon.load(category.iconUrl, RetrofitClient.imageLoader) {
                crossfade(true)
                placeholder(R.drawable.bg_image_placeholder)
                error(R.drawable.bg_image_error)

                listener(
                    onStart = { request ->
                        Log.d("CoilDebug", "Starting to load image: ${request.data}")
                    },
                    onError = { request, result ->
                        Log.e("CoilDebug", "ERROR loading image: ${request.data}")
                        // THIS WILL PRINT THE EXACT ERROR REASON
                        result.throwable.printStackTrace()
                    },
                    onSuccess = { request, result ->
                        Log.d("CoilDebug", "SUCCESS loading image: ${request.data}")
                    }
                )
            }
        }
    }

    // Function to update the data and filter
    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newList: List<CategoryPublic>) {
        categories = newList
        notifyDataSetChanged() // This is simple, but for large lists DiffUtil is better
    }
}