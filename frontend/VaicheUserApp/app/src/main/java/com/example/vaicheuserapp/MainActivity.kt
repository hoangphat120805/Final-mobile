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
        categoryAdapter = CategoryAdapter(emptyList())
        binding.rvCategories.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 2)
            adapter = categoryAdapter
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener { text ->
            val query = text.toString().lowercase().trim()
            val filteredList = if (query.isEmpty()) {
                allCategories
            } else {
                allCategories.filter { it.name.lowercase().contains(query) }
            }
            categoryAdapter.updateData(filteredList)
        }
    }

    private fun fetchCategories() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getCategories()
                if (response.isSuccessful && response.body() != null) {
                    allCategories = response.body()!!
                    categoryAdapter.updateData(allCategories)
                } else {
                    Toast.makeText(this@MainActivity, "Failed to load categories", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}