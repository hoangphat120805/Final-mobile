package com.example.vaicheuserapp.ui.notifications

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController // <-- New import
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.vaicheuserapp.R
import com.example.vaicheuserapp.data.model.NotificationPublic
import com.example.vaicheuserapp.data.network.RetrofitClient
import com.example.vaicheuserapp.databinding.FragmentNotificationListBinding
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Implement the click listener interface
class NotificationListFragment : Fragment(), OnNotificationClickListener {

    private var _binding: FragmentNotificationListBinding? = null
    private val binding get() = _binding!!

    private lateinit var notificationAdapter: NotificationAdapter
    private var allNotifications = listOf<NotificationPublic>()

    private val BACKEND_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        fetchNotifications()
    }

    private fun setupRecyclerView() {
        notificationAdapter = NotificationAdapter(this) // Pass 'this' as listener
        binding.rvNotifications.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = notificationAdapter
        }
    }

    private fun fetchNotifications() {
        binding.pbLoadingNotifications.visibility = View.VISIBLE
        binding.tvNoNotifications.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getNotifications()
                if (response.isSuccessful && response.body() != null) {
                    var fetchedNotifications = response.body()!!

                    // --- CRITICAL FIX: Sort notifications by creation time (newest first) ---
                    fetchedNotifications = fetchedNotifications.sortedByDescending { notification ->
                        try {
                            LocalDateTime.parse(notification.createdAt, BACKEND_DATETIME_FORMATTER)
                        } catch (e: Exception) {
                            Log.e("NotificationList", "Error parsing createdAt for notification ${notification.id}: ${e.message}")
                            LocalDateTime.MIN // Place unparseable dates at the end
                        }
                    }

                    allNotifications = fetchedNotifications
                    if (allNotifications.isNotEmpty()) {
                        notificationAdapter.submitList(allNotifications)
                        binding.rvNotifications.visibility = View.VISIBLE
                    } else {
                        binding.tvNoNotifications.visibility = View.VISIBLE
                        binding.rvNotifications.visibility = View.GONE
                    }
                } else {
                    Log.e("Notifications", "Failed to load notifications: ${response.code()} - ${response.errorBody()?.string()}")
                    Toast.makeText(requireContext(), "Failed to load notifications", Toast.LENGTH_SHORT).show()
                    binding.tvNoNotifications.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                Log.e("Notifications", "Error fetching notifications: ${e.message}", e)
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.tvNoNotifications.visibility = View.VISIBLE
            } finally {
                binding.pbLoadingNotifications.visibility = View.GONE
            }
        }
    }

    override fun onNotificationClick(notification: NotificationPublic) {
        Log.d("Notifications", "Clicked notification: ${notification.id}, Read Status: ${notification.isRead}")

        // --- NEW: Mark as read if it's currently unread ---
        if (!notification.isRead) {
            markNotificationAsRead(notification.id)
            // Optimistically update the local list immediately for better UX
            val updatedList = allNotifications.map {
                if (it.id == notification.id) it.copy(isRead = true) else it
            }
            allNotifications = updatedList
            notificationAdapter.submitList(updatedList)
        }

        // Launch NotificationDetailActivity directly, passing the notification object
        // Pass a copy that reflects the new read status for the detail screen
        val intent = Intent(requireContext(), NotificationDetailActivity::class.java)
        intent.putExtra("EXTRA_NOTIFICATION", notification.copy(isRead = true))
        startActivity(intent)
    }

    private fun markNotificationAsRead(notificationId: String) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.markNotificationAsRead(notificationId)
                if (response.isSuccessful) {
                    Log.d("Notifications", "Notification $notificationId marked as read on backend.")
                    // UI is already optimistically updated. No need to re-fetch unless list becomes stale.
                } else {
                    Log.e("Notifications", "Failed to mark notification $notificationId as read: ${response.code()} - ${response.errorBody()?.string()}")
                    // If backend failed, you might want to revert the optimistic UI update
                }
            } catch (e: Exception) {
                Log.e("Notifications", "Error marking notification as read: ${e.message}", e)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}