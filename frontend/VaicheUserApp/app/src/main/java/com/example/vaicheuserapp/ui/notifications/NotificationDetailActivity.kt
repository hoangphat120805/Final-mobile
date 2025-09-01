package com.example.vaicheuserapp.ui.notifications

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope // <-- New import
import com.example.vaicheuserapp.data.model.NotificationPublic
import com.example.vaicheuserapp.data.network.RetrofitClient // <-- New import
import com.example.vaicheuserapp.databinding.ActivityNotificationDetailBinding
import kotlinx.coroutines.launch // <-- New import
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class NotificationDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationDetailBinding

    private val BACKEND_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()

        val notification = getNotificationFromIntent()

        notification?.let {
            displayNotificationDetails(it)
            // --- REMOVED: Mark as read here. It's now handled by NotificationListFragment.
            // If the activity is launched, it means the user clicked it, so it's already "read" or will be marked by the list fragment.
        } ?: run {
            Toast.makeText(this, "Notification details not found.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun getNotificationFromIntent(): NotificationPublic? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("EXTRA_NOTIFICATION", NotificationPublic::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("EXTRA_NOTIFICATION")
        }
    }

    private fun setupListeners() {
        binding.ivBackButton.setOnClickListener {
            finish()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun displayNotificationDetails(notification: NotificationPublic) {
        binding.tvDetailNotificationTitle.text = notification.title // <-- Use title
        binding.tvDetailNotificationMessage.text = notification.message
        binding.tvDetailNotificationDate.text = formatDateTime(notification.createdAt)
    }

    private fun formatDateTime(utcDateTimeString: String): String {
        return try {
            val localDateTime = LocalDateTime.parse(utcDateTimeString, BACKEND_DATETIME_FORMATTER) // Use custom formatter
            val dateTimeInVietnam = localDateTime.atZone(ZoneId.of("UTC")).withZoneSameInstant(ZoneId.of("Asia/Ho_Chi_Minh"))
            val dateFormatter = DateTimeFormatter.ofPattern("HH:mm, dd/MM/yyyy", Locale("vi", "VN"))
            dateTimeInVietnam.format(dateFormatter)
        } catch (e: Exception) {
            Log.e("NotificationDetail", "Error parsing or formatting date/time: $utcDateTimeString - ${e.message}")
            utcDateTimeString
        }
    }

    // --- NEW: markNotificationAsRead function (calls API) ---
    private fun markNotificationAsRead(notificationId: String) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.markNotificationAsRead(notificationId)
                if (response.isSuccessful) {
                    Log.d("NotificationDetail", "Notification $notificationId marked as read on backend from detail view.")
                    // No need to update UI here, as the previous screen will re-fetch on resume
                } else {
                    Log.e("NotificationDetail", "Failed to mark notification $notificationId as read from detail view: ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e("NotificationDetail", "Error marking notification as read from detail view: ${e.message}", e)
            }
        }
    }
}