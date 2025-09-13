package com.example.vaiche_driver.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.vaiche_driver.R
import com.example.vaiche_driver.model.UserNotification
import com.example.vaiche_driver.ui.notifications.NotificationAdapter
import com.example.vaiche_driver.ui.notifications.OnNotificationClickListener
import com.example.vaiche_driver.viewmodel.NotificationViewModel

class NotificationsFragment : Fragment(), OnNotificationClickListener {

    private val viewModel: NotificationViewModel by viewModels()
    private lateinit var adapter: NotificationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_notifications, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rv = view.findViewById<RecyclerView>(R.id.rv_notifications)
        val progress = view.findViewById<View>(R.id.pb_loading_notifications)
        adapter = NotificationAdapter(this)
        rv.adapter = adapter
        rv.layoutManager = LinearLayoutManager(requireContext())

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            progress.visibility = if (loading) View.VISIBLE else View.GONE
        }
        viewModel.notifications.observe(viewLifecycleOwner) { notifications ->
            adapter.submitList(notifications)
            if (notifications.isEmpty()) {
                view.findViewById<View>(R.id.tv_no_notifications).visibility = View.VISIBLE
            } else {
                view.findViewById<View>(R.id.tv_no_notifications).visibility = View.GONE
            }
        }
        viewModel.load()
    }

    override fun onNotificationClick(notification: UserNotification) {
        if (!notification.isRead) {
            viewModel.markAsRead(notification.id)
        }
        parentFragmentManager.beginTransaction()
            .replace(
                R.id.fragment_container,
                NotificationDetailFragment.newInstance(notification.id)
            )
            .addToBackStack(null)
            .commit()
    }
}
