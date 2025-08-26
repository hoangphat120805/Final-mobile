package com.example.vaicheuserapp

import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import coil.load
import com.example.vaicheuserapp.data.model.CategoryPublic
import com.example.vaicheuserapp.data.network.RetrofitClient
import com.example.vaicheuserapp.databinding.ActivityScrapDetailBinding

class ScrapDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScrapDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScrapDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get the Category object from the intent
        val category = getCategoryFromIntent()

        category?.let {
            populateUi(it)
        }

        setupListeners()
    }

    private fun getCategoryFromIntent(): CategoryPublic? {
        // Modern, type-safe way to get a Parcelable extra
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("EXTRA_CATEGORY", CategoryPublic::class.java)
        } else {
            // Deprecated but required for older Android versions
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("EXTRA_CATEGORY")
        }
    }

    private fun populateUi(category: CategoryPublic) {
        // Update the custom toolbar's title TextView
        binding.tvToolbarTitle.text = category.name
        binding.tvDescription.text = "• ${category.description}"

        binding.ivScrapImageLarge.load(category.iconUrl, RetrofitClient.imageLoader) {
            crossfade(true)
            placeholder(R.drawable.bg_image_placeholder) // Use your placeholder
            error(R.drawable.bg_image_error) // Use your error placeholder
        }
    }

    private fun setupListeners() {
        // Handle the custom back button ImageView click
        binding.ivBackButton.setOnClickListener {
            finish() // Closes the current activity and goes back to the previous one
        }
    }
}