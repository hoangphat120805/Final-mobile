package com.example.vaicheuserapp.ui.notifications

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.vaicheuserapp.ChatActivity // <-- NEW IMPORT: The chat activity
import com.example.vaicheuserapp.R
import com.example.vaicheuserapp.data.model.ConversationWithLastMessage
import com.example.vaicheuserapp.data.model.UserPublic
import com.example.vaicheuserapp.data.network.RetrofitClient
import com.example.vaicheuserapp.databinding.FragmentMessageListBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

class MessageListFragment : Fragment(), OnConversationClickListener {

    private var _binding: FragmentMessageListBinding? = null
    private val binding get() = _binding!!

    private lateinit var conversationListAdapter: ConversationListAdapter
    private var allConversations = listOf<ConversationWithLastMessage>()
    private var currentUserId: String = "" // Will be set from shared preferences

    private val userProfileCache = mutableMapOf<String, UserPublic>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMessageListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Get current user ID (assuming it's stored in shared preferences after login)
        currentUserId = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getString("user_id", "") ?: ""

        if (currentUserId.isEmpty()) {
            Log.e("MessageListFragment", "Current user ID not found. Cannot fetch conversations.")
            Toast.makeText(requireContext(), "User not logged in.", Toast.LENGTH_SHORT).show()
            // Optionally redirect to login
            return
        }

        setupRecyclerView()
        fetchConversations()
    }

    override fun onResume() {
        super.onResume()
        // Refresh conversations every time the fragment is resumed
        fetchConversations()
    }

    private fun setupRecyclerView() {
        // --- CRITICAL FIX: Pass the userProfileCache to the adapter ---
        conversationListAdapter = ConversationListAdapter(this, currentUserId, userProfileCache)
        binding.rvConversations.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = conversationListAdapter
        }
    }

    private suspend fun fetchAndCacheUserProfile(userId: String): UserPublic? { // Make it suspend
        // Check cache first
        userProfileCache[userId]?.let { cachedUser ->
            return cachedUser
        }

        // If not in cache, fetch from API
        return try {
            val response = RetrofitClient.instance.getUser(userId) // Use the GET /api/user/{user_id} API
            if (response.isSuccessful && response.body() != null) {
                val user = response.body()!!
                userProfileCache[userId] = user // Cache the user
                user
            } else {
                Log.e("MessageListFragment", "Failed to fetch user $userId: ${response.code()} - ${response.errorBody()?.string()}")
                null
            }
        } catch (e: Exception) {
            Log.e("MessageListFragment", "Error fetching user $userId: ${e.message}", e)
            null
        }
    }

    private fun fetchConversations() {
        binding.pbLoadingConversations.visibility = View.VISIBLE
        binding.tvNoConversations.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getConversations()
                if (response.isSuccessful && response.body() != null) {
                    allConversations = response.body()!!
                    if (allConversations.isNotEmpty()) {
                        // --- NEW: Pre-fetch and cache all relevant user profiles ---
                        // Collect all unique user IDs from all conversations
                        val allUserIds = allConversations
                            .flatMap { it.members?.map { member -> member.userId } ?: emptyList() } // Correctly get member IDs
                            .distinct()

                        // Fetch all unique user profiles concurrently
                        val deferredUsers = allUserIds.map { userId ->
                            async(Dispatchers.IO) {
                                fetchAndCacheUserProfile(userId) // Call suspend function
                            }
                        }
                        deferredUsers.awaitAll() // Wait for all user fetches to complete

                        conversationListAdapter.submitList(allConversations)
                        binding.rvConversations.visibility = View.VISIBLE
                    } else {
                        binding.tvNoConversations.visibility = View.VISIBLE
                        binding.rvConversations.visibility = View.GONE
                    }
                } else {
                    Log.e("MessageListFragment", "Failed to load conversations: ${response.code()} - ${response.errorBody()?.string()}")
                    Toast.makeText(requireContext(), "Failed to load conversations", Toast.LENGTH_SHORT).show()
                    binding.tvNoConversations.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                Log.e("MessageListFragment", "Error fetching conversations: ${e.message}", e)
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.tvNoConversations.visibility = View.VISIBLE
            } finally {
                binding.pbLoadingConversations.visibility = View.GONE
            }
        }
    }

    override fun onConversationClick(conversation: ConversationWithLastMessage) {
        Log.d("MessageListFragment", "Clicked conversation: ${conversation.id}")
        // Launch ChatActivity
        val intent = Intent(requireContext(), ChatActivity::class.java)
        intent.putExtra("EXTRA_CONVERSATION", conversation) // Pass Parcelable conversation object
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}