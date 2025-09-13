package com.example.vaiche_driver.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.vaiche_driver.R
import com.example.vaiche_driver.data.local.SessionManager
import com.example.vaiche_driver.ui.messages.MessageThreadAdapter
import com.example.vaiche_driver.viewmodel.Event
import com.example.vaiche_driver.viewmodel.MessagesThreadViewModel
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MessagesThreadFragment : Fragment() {

    companion object {
        private const val ARG_CONV_ID = "conv_id"
        private const val ARG_MY_ID = "my_user_id"
        private const val ARG_PARTNER_NAME = "partner_name"
        private const val ARG_PARTNER_AVT = "partner_avt_url"

        fun newInstance(
            conversationId: String,
            myUserId: String,
            partnerName: String? = null,
            partnerAvatarUrl: String? = null
        ) = MessagesThreadFragment().apply {
            arguments = bundleOf(
                ARG_CONV_ID to conversationId,
                ARG_MY_ID to myUserId,
                ARG_PARTNER_NAME to partnerName,
                ARG_PARTNER_AVT to partnerAvatarUrl
            )
        }
    }

    private val vm: MessagesThreadViewModel by viewModels()
    private lateinit var adapter: MessageThreadAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        return inflater.inflate(R.layout.fragment_message, container, false)
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)

        val convId = requireArguments().getString(ARG_CONV_ID)!!
        val myId   = requireArguments().getString(ARG_MY_ID)!!

        // ===== Toolbar =====
        view.findViewById<ImageView>(R.id.iv_back_button)?.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        view.findViewById<TextView>(R.id.tv_toolbar_title)?.let { tv ->
            tv.text = arguments?.getString(ARG_PARTNER_NAME) ?: "Chat"
        }
        view.findViewById<ImageView>(R.id.iv_chat_toolbar_avatar)?.let { iv ->
            val url = arguments?.getString(ARG_PARTNER_AVT)
            if (!url.isNullOrBlank()) {
                // Cần Glide dependency
                Glide.with(iv.context)
                    .load(url)
                    .placeholder(R.drawable.ic_person_circle)
                    .error(R.drawable.ic_person_circle)
                    .into(iv)
            }
        }

        // ===== RecyclerView =====
        val rv = view.findViewById<RecyclerView>(R.id.rv_chat_messages)
        adapter = MessageThreadAdapter()
        rv.adapter = adapter
        rv.layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }

        vm.rows.observe(viewLifecycleOwner) {
            adapter.submitList(it) { rv.scrollToPosition(adapter.itemCount - 1) }
        }
        vm.isLoading.observe(viewLifecycleOwner) { loading ->
            view.findViewById<ProgressBar>(R.id.pb_loading_messages)?.visibility =
                if (loading) View.VISIBLE else View.GONE
        }

        // ===== Load lần đầu =====
        vm.load(convId, myId)

        // ===== WS connect (thêm token) =====
        val token = SessionManager(requireContext()).fetchAuthToken()
        if (token.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Thiếu token đăng nhập", Toast.LENGTH_SHORT).show()
        } else {
            // Đặt base URL theo backend của bạn
            vm.connectWs(baseHttpUrl = "http://160.30.192.11:8000", token = token)
        }

        // Toast từ ViewModel
        vm.toast.observe(viewLifecycleOwner) { e: Event<String> ->
            e.getContentIfNotHandled()?.let { msg ->
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
        }

        // ===== Gửi tin qua WS + appendLocal =====
        val et = view.findViewById<EditText>(R.id.et_message_input)
        val btnSend = view.findViewById<FloatingActionButton>(R.id.btn_send_message)
        btnSend.setOnClickListener {
            val text = et.text?.toString()?.trim().orEmpty()
            if (text.isEmpty()) return@setOnClickListener
            vm.sendViaWs(convId, text)          // gửi lên WS
            vm.appendLocal(convId, myId, text)  // hiển thị ngay
            et.setText("")
            rv.post { rv.scrollToPosition(adapter.itemCount - 1) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        vm.closeWs()
    }
}
