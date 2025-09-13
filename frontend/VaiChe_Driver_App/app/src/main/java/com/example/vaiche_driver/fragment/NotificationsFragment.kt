package com.example.vaiche_driver.fragment

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.vaiche_driver.R
import com.example.vaiche_driver.data.network.RetrofitClient
import com.example.vaiche_driver.model.UserNotification
import com.example.vaiche_driver.ui.messages.ConversationsAdapter
import com.example.vaiche_driver.ui.notifications.NotificationAdapter
import com.example.vaiche_driver.ui.notifications.OnNotificationClickListener
import com.example.vaiche_driver.viewmodel.ConversationsViewModel
import com.example.vaiche_driver.viewmodel.NotificationViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotificationsFragment : Fragment(), OnNotificationClickListener {

    private val notiVm: NotificationViewModel by viewModels()
    private val convVm: ConversationsViewModel by viewModels()

    private lateinit var rv: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var progress: View
    private lateinit var btnNoti: com.google.android.material.button.MaterialButton
    private lateinit var btnMsg: com.google.android.material.button.MaterialButton

    private lateinit var notiAdapter: NotificationAdapter
    private lateinit var convAdapter: ConversationsAdapter

    private var myUserId: String? = null

    // giữ trạng thái tab & scroll
    companion object {
        private var lastSelectedTab: Tab = Tab.NOTI
        private var savedRvState: Parcelable? = null
    }
    enum class Tab { NOTI, MSG }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_notifications, container, false)

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)
        rv = view.findViewById(R.id.rv_list)
        tvEmpty = view.findViewById(R.id.tv_empty)
        progress = view.findViewById(R.id.pb_loading)
        btnNoti = view.findViewById(R.id.btn_notifications)
        btnMsg = view.findViewById(R.id.btn_messages)

        view.findViewById<TextView>(R.id.toolbar_title)?.text = "Inbox"

        rv.layoutManager = LinearLayoutManager(requireContext())
        notiAdapter = NotificationAdapter(this)
        convAdapter = ConversationsAdapter { row ->
            val uid = myUserId ?: return@ConversationsAdapter
            parentFragmentManager.beginTransaction()
                .replace(
                    R.id.fragment_container,
                    MessagesThreadFragment.newInstance(
                        conversationId = row.id,
                        myUserId = uid,
                        partnerName = row.name,
                        partnerAvatarUrl = row.avatarUrl
                    )
                )
                .addToBackStack(null)
                .commit()
        }

        btnNoti.setOnClickListener { switchTo(Tab.NOTI) }
        btnMsg.setOnClickListener { switchTo(Tab.MSG) }

        // phục hồi tab trước khi rời màn hình
        switchTo(lastSelectedTab)
    }

    override fun onPause() {
        super.onPause()
        savedRvState = rv.layoutManager?.onSaveInstanceState()
    }

    override fun onResume() {
        super.onResume()
        savedRvState?.let { st ->
            rv.layoutManager?.onRestoreInstanceState(st)
            savedRvState = null
        }
    }

    private fun switchTo(tab: Tab) {
        lastSelectedTab = tab
        styleTabs(tab)

        when (tab) {
            Tab.NOTI -> {
                rv.adapter = notiAdapter
                notiVm.isLoading.observe(viewLifecycleOwner) { progress.visibility = if (it) View.VISIBLE else View.GONE }
                notiVm.notifications.observe(viewLifecycleOwner) { list ->
                    notiAdapter.submitList(list)
                    tvEmpty.visibility = if (list.isNullOrEmpty()) View.VISIBLE else View.GONE
                    tvEmpty.text = "No notifications yet."
                }
                notiVm.load()
            }
            Tab.MSG -> {
                rv.adapter = convAdapter
                convVm.isLoading.observe(viewLifecycleOwner) { progress.visibility = if (it) View.VISIBLE else View.GONE }
                convVm.items.observe(viewLifecycleOwner) { list ->
                    convAdapter.submitList(list)
                    tvEmpty.visibility = if (list.isNullOrEmpty()) View.VISIBLE else View.GONE
                    tvEmpty.text = "No conversations."
                }
                ensureMyUserId { convVm.load(it) }
            }
        }
    }

    private fun styleTabs(selected: Tab) {
        val teal = android.graphics.Color.parseColor("#2EC4B6")
        val white = android.graphics.Color.parseColor("#FFFFFF")
        val black = android.graphics.Color.parseColor("#000000")
        val sel = if (selected == Tab.NOTI) btnNoti else btnMsg
        val uns = if (selected == Tab.NOTI) btnMsg else btnNoti

        sel.setTextColor(white)
        sel.backgroundTintList = android.content.res.ColorStateList.valueOf(teal)
        uns.setTextColor(black)
        uns.backgroundTintList = android.content.res.ColorStateList.valueOf(white)
    }

    private fun ensureMyUserId(onReady: (String) -> Unit) {
        myUserId?.let { onReady(it); return }
        viewLifecycleOwner.lifecycleScope.launch {
            val me = withContext(Dispatchers.IO) {
                runCatching { RetrofitClient.instance.getMe().body() }.getOrNull()
            }
            val id = me?.id
            if (id != null) { myUserId = id; onReady(id) }
            else { tvEmpty.visibility = View.VISIBLE; tvEmpty.text = "Can't load your profile." }
        }
    }

    override fun onNotificationClick(notification: UserNotification) {
        if (!notification.isRead) notiVm.markAsRead(notification.id)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, NotificationDetailFragment.newInstance(notification.id))
            .addToBackStack(null)
            .commit()
    }
}
