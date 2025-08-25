package com.example.vaicheuserapp

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.vaicheuserapp.data.model.CategoryPublic
import com.example.vaicheuserapp.data.network.RetrofitClient
import com.example.vaicheuserapp.databinding.ActivityMainBinding
import com.example.vaicheuserapp.ui.dashboard.CategoryAdapter
import kotlinx.coroutines.launch
import android.content.Intent
import kotlin.jvm.java

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var categoryAdapter: CategoryAdapter
    private var allCategories = listOf<CategoryPublic>() // Store the full list

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupSearch()
        fetchCategories()
    }

    private fun setupRecyclerView() {
        categoryAdapter = CategoryAdapter { clickedCategory ->
            val intent = Intent(this, ScrapDetailActivity::class.java)
            intent.putExtra("EXTRA_CATEGORY", clickedCategory)
            startActivity(intent)
        }
        binding.rvCategories.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 2)
            adapter = categoryAdapter
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener { text ->
            val rawQuery = text.toString().trim()

            val normalizedQuery = rawQuery.lowercase().normalizeVietnamese()

            val filteredList = if (normalizedQuery.isEmpty()) {
                allCategories
            } else {
                allCategories.filter { category ->
                    // 2. Normalize the category name before comparing
                    category.name.lowercase().normalizeVietnamese().contains(normalizedQuery)
                }
            }
            categoryAdapter.submitList(filteredList)
        }
    }

    private fun fetchCategories() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getCategories()
                if (response.isSuccessful && response.body() != null) {
                    allCategories = response.body()!!
                    categoryAdapter.submitList(allCategories)
                } else {
                    Toast.makeText(this@MainActivity, "Failed to load categories", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}