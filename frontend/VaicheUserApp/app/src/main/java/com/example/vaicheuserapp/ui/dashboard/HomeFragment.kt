package com.example.vaicheuserapp.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment // Import Fragment
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.vaicheuserapp.CategoryCache
import com.example.vaicheuserapp.R
import com.example.vaicheuserapp.normalizeVietnamese // Make sure this is accessible
import com.example.vaicheuserapp.data.model.CategoryPublic
import com.example.vaicheuserapp.data.network.RetrofitClient
import com.example.vaicheuserapp.databinding.FragmentHomeBinding // Change this to FragmentHomeBinding
import com.example.vaicheuserapp.ScrapDetailActivity
import kotlinx.coroutines.launch

class HomeFragment : Fragment() { // Extend Fragment

    private var _binding: FragmentHomeBinding? = null // Change binding type
    private val binding get() = _binding!!

    private lateinit var categoryAdapter: CategoryAdapter
    private var allCategories = listOf<CategoryPublic>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("HomeFragment", "onViewCreated called.")

        setupRecyclerView()
        setupSearch()
        fetchCategories()
        // No bottom nav setup here, MainActivity handles it
    }

    private fun setupRecyclerView() {
        categoryAdapter = CategoryAdapter { clickedCategory ->
            Log.d("HomeFragment", "Clicked on category: ${clickedCategory.name}")
            val intent = Intent(requireContext(), ScrapDetailActivity::class.java) // Use requireContext()
            intent.putExtra("EXTRA_CATEGORY", clickedCategory)
            startActivity(intent)
        }
        binding.rvCategories.apply {
            layoutManager = GridLayoutManager(requireContext(), 2) // Use requireContext()
            adapter = categoryAdapter
        }
        Log.d("HomeFragment", "RecyclerView setup complete.")
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener { text ->
            val rawQuery = text.toString().trim()
            Log.d("HomeFragment", "Search query entered: '$rawQuery'")
            val normalizedQuery = rawQuery.lowercase().normalizeVietnamese()
            Log.d("HomeFragment", "Normalized query: '$normalizedQuery'")

            val filteredList = if (normalizedQuery.isEmpty()) {
                allCategories
            } else {
                allCategories.filter { category ->
                    category.name.lowercase().normalizeVietnamese().contains(normalizedQuery)
                }
            }
            Log.d("HomeFragment", "Filtered list size: ${filteredList.size}")
            categoryAdapter.submitList(filteredList)
            Log.d("HomeFragment", "Submitted filtered list to adapter.")
        }
        Log.d("HomeFragment", "Search listener setup complete.")
    }

    private fun fetchCategories() {
        Log.d("HomeFragment", "Starting to fetch categories...")
        lifecycleScope.launch { // lifecycleScope works here because Fragment has a lifecycle
            try {
                val response = RetrofitClient.instance.getCategories()
                if (response.isSuccessful && response.body() != null) {
                    allCategories = response.body()!!
                    CategoryCache.setCategories(allCategories)
                    Log.d("HomeFragment", "Fetched ${allCategories.size} categories.")
                    categoryAdapter.submitList(allCategories)
                    Log.d("HomeFragment", "Submitted all categories to adapter.")
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("HomeFragment", "Failed to load categories: $errorBody")
                    Toast.makeText(requireContext(), "Failed to load categories", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("HomeFragment", "Error fetching categories: ${e.message}", e)
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Clear binding reference
    }
}