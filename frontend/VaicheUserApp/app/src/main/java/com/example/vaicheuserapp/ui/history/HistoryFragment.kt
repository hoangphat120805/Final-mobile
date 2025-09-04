package com.example.vaicheuserapp.ui.history

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.vaicheuserapp.R
import com.example.vaicheuserapp.data.model.OrderPublic
import com.example.vaicheuserapp.data.network.RetrofitClient
import com.example.vaicheuserapp.databinding.FragmentHistoryBinding
import kotlinx.coroutines.launch

class HistoryFragment : Fragment(), OnOrderClickListener {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var orderHistoryAdapter: OrderHistoryAdapter
    private var allOrders = listOf<OrderPublic>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        fetchOrderHistory()
    }

    private fun setupRecyclerView() {
        orderHistoryAdapter = OrderHistoryAdapter(this) // Pass 'this' as listener
        binding.rvOrderHistory.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = orderHistoryAdapter
        }
    }

    private fun fetchOrderHistory() {
        binding.pbLoadingHistory.visibility = View.VISIBLE
        binding.tvNoHistory.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getOrders() // Call the new API method
                if (response.isSuccessful && response.body() != null) {
                    allOrders = response.body()!!
                    if (allOrders.isNotEmpty()) {
                        orderHistoryAdapter.submitList(allOrders)
                        binding.rvOrderHistory.visibility = View.VISIBLE
                    } else {
                        binding.tvNoHistory.visibility = View.VISIBLE
                        binding.rvOrderHistory.visibility = View.GONE
                    }
                } else {
                    Log.e("HistoryFragment", "Failed to load order history: ${response.code()} - ${response.errorBody()?.string()}")
                    Toast.makeText(requireContext(), "Failed to load order history", Toast.LENGTH_SHORT).show()
                    binding.tvNoHistory.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                Log.e("HistoryFragment", "Error fetching order history: ${e.message}", e)
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.tvNoHistory.visibility = View.VISIBLE
            } finally {
                binding.pbLoadingHistory.visibility = View.GONE
            }
        }
    }

    override fun onOrderClick(order: OrderPublic) {
        Log.d("HistoryFragment", "Clicked order: ${order.id}, Status: ${order.status}")
        Toast.makeText(requireContext(), "Clicked order ${order.id}", Toast.LENGTH_SHORT).show()
        // TODO: Navigate to OrderDetailActivity/Fragment if you create one
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}