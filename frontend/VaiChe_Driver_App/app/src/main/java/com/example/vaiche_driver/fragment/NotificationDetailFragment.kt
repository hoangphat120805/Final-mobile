package com.example.vaiche_driver.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.vaiche_driver.R
import com.example.vaiche_driver.data.network.RetrofitClient
import com.example.vaiche_driver.data.repository.NotificationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalQueries
import java.util.Locale

class NotificationDetailFragment : Fragment() {

    companion object {
        private const val ARG_NOTIFICATION_ID = "notification_id"

        fun newInstance(notificationId: String) = NotificationDetailFragment().apply {
            arguments = bundleOf(ARG_NOTIFICATION_ID to notificationId)
        }

        // Always show full date/time (local timezone)
        private val OUT_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale("en", "US"))

        /**
         * Convert ISO-8601 string into dd/MM/yyyy HH:mm in local timezone.
         * Supports:
         *  - 2025-09-08T10:10:04.296622Z
         *  - 2025-09-08T10:10:04.296+07:00
         *  - 2025-09-08T10:10:04   (no offset → assume UTC)
         */
        fun formatIsoToDateTime(raw: String?): String {
            if (raw.isNullOrBlank()) return ""

            val zdtLocal = runCatching {
                val parsed = DateTimeFormatter.ISO_DATE_TIME.parse(raw)
                val offset = parsed.query(TemporalQueries.offset())
                val systemZone = ZoneId.systemDefault()
                if (offset != null) {
                    OffsetDateTime.from(parsed).atZoneSameInstant(systemZone)
                } else {
                    LocalDateTime.from(parsed)
                        .atOffset(ZoneOffset.UTC)
                        .atZoneSameInstant(systemZone)
                }
            }.getOrNull()

            return zdtLocal?.format(OUT_FMT)
                ?: raw.replace('T', ' ').take(19) // fallback
        }
    }

    private val repo = NotificationRepository { RetrofitClient.instance }
    private lateinit var notificationId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationId = requireArguments().getString(ARG_NOTIFICATION_ID).orEmpty()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_notification_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvTitle = view.findViewById<TextView>(R.id.tv_detail_notification_title)
        val tvTime = view.findViewById<TextView>(R.id.tv_detail_notification_date)
        val tvMessage = view.findViewById<TextView>(R.id.tv_detail_notification_message)
        val backBtn = view.findViewById<ImageView>(R.id.iv_back_button)

        backBtn?.setOnClickListener { parentFragmentManager.popBackStack() }

        viewLifecycleOwner.lifecycleScope.launch {
            val notif = withContext(Dispatchers.IO) {
                repo.getNotifications().getOrNull()?.find { it.id == notificationId }
            }
            if (notif != null) {
                tvTitle?.text = notif.title
                tvTime?.text = formatIsoToDateTime(notif.createdAt)
                tvMessage?.text = notif.message
            } else {
                tvTitle?.text = "Notification not found"
                tvTime?.text = ""
                tvMessage?.text = ""
            }
        }
    }
}
