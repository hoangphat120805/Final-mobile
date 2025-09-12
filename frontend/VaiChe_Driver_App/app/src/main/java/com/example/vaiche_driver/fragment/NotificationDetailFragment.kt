package com.example.vaiche_driver.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
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

class NotificationDetailFragment : Fragment() {
    companion object {
        private const val ARG_NOTIFICATION_ID = "notification_id"
        fun newInstance(notificationId: String) = NotificationDetailFragment().apply {
            arguments = bundleOf(
                ARG_NOTIFICATION_ID to notificationId
            )
        }
    }

    private val repo = NotificationRepository { RetrofitClient.instance }

    private lateinit var notificationId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationId = requireArguments().getString(ARG_NOTIFICATION_ID).orEmpty()
    }

    override fun onCreateView(inflater: LayoutInflater, container: android.view.ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_notification_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Load notification details from repository
        val tvTitle = view.findViewById<TextView>(R.id.tv_detail_notification_title)
        val tvTime = view.findViewById<TextView>(R.id.tv_detail_notification_date)
        val tvMessage = view.findViewById<TextView>(R.id.tv_detail_notification_message)
        val backBtn = view.findViewById<ImageView>(R.id.iv_back_button)

        backBtn?.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val cat = withContext(Dispatchers.IO) {
                repo.getNotifications().getOrNull()?.find { it.id == notificationId }
            }
            if (cat != null) {
                tvTitle?.text = cat.title
                tvTime?.text = cat.createdAt
                tvMessage?.text = cat.message
            } else {
                tvTitle?.text = "Notification not found"
                tvTime?.text = ""
                tvMessage?.text = ""
            }
        }
    }
}