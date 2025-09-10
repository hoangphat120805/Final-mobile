package com.example.vaiche_driver.fragment

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.example.vaiche_driver.R
import com.example.vaiche_driver.model.NearbyOrderPublic
import com.example.vaiche_driver.model.localStatus
import com.example.vaiche_driver.model.toSchedule
import com.example.vaiche_driver.viewmodel.NewOrderViewModel
import com.example.vaiche_driver.viewmodel.SharedViewModel
import com.google.gson.Gson
import kotlin.math.roundToInt

class NewOrderDialogFragment : DialogFragment() {

    private val viewModel: NewOrderViewModel by viewModels()
    private val sharedViewModel: SharedViewModel by activityViewModels()

    private var orderId: String? = null
    private var seedNearby: NearbyOrderPublic? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.TransparentDialogTheme)

        // Read args
        val args = requireArguments()
        orderId = args.getString(ARG_ORDER_ID)

        args.getString(ARG_ORDER_SEED_JSON)?.let { json ->
            seedNearby = runCatching { Gson().fromJson(json, NearbyOrderPublic::class.java) }.getOrNull()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_new_order, container, false)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val acceptButton = view.findViewById<Button>(R.id.btn_accept)
        val closeButton = view.findViewById<ImageView>(R.id.iv_close)

        acceptButton.isEnabled = false

        // Nếu có seed từ Nearby -> prime vào VM để UI có distance/time tức thì
        viewModel.primeWithSeed(seedNearby)

        // Quan sát dữ liệu để bind UI
        viewModel.order.observe(viewLifecycleOwner) { order ->
            if (order != null) {
                bindDataToViews(view, order)
                acceptButton.isEnabled = true
            } else {
                acceptButton.isEnabled = false
            }
        }

        // Lần đầu: vẫn fetch detail theo id để bổ sung thông tin khác
        if (savedInstanceState == null) {
            viewModel.loadOrderDetails(orderId)
        }

        // Accept
        acceptButton.setOnClickListener {
            val id = orderId
            if (id == null) {
                Toast.makeText(context, "Missing order id", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.acceptOrder(id) { acceptedOrder ->
                val schedule = acceptedOrder.toSchedule(acceptedOrder.localStatus())
                sharedViewModel.onOrderAccepted(schedule)
                dismissAllowingStateLoss()
            }
        }

        // Close / Reject
        closeButton.setOnClickListener {
            val id = orderId
            if (id != null) {
                viewModel.rejectOrder(id) { rejectedId ->
                    sharedViewModel.onOrderRejected(rejectedId)
                    dismissAllowingStateLoss()
                }
            } else {
                dismissAllowingStateLoss()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun bindDataToViews(view: View, order: NearbyOrderPublic) {
        val titleText = view.findViewById<TextView>(R.id.tv_title)
        val startName = view.findViewById<TextView>(R.id.tv_start_location_name)
        val startAddress = view.findViewById<TextView>(R.id.tv_start_location_address)
        val endName = view.findViewById<TextView>(R.id.tv_end_location_name)
        val endAddress = view.findViewById<TextView>(R.id.tv_end_location_address)
        val distanceTime = view.findViewById<TextView>(R.id.tv_distance_time)

        titleText.text = "Nearby order"
        startName.text = order.pickupAddress
        startAddress.text = order.pickupAddress
        endName.text = "VaiChe"
        endAddress.text = "Hồ Chí Minh"

        distanceTime.text = formatDistanceTime(order.distanceKm, order.travelTimeSeconds)
    }

    private fun formatDistanceTime(distanceKm: Double?, travelTimeSeconds: Double?): String {
        // ---- Distance ----
        val distancePart = distanceKm?.let {
            if (it < 1.0) {
                val meters = (it * 1000).toInt()
                "$meters m"
            } else {
                val km = String.format("%.1f", it)
                "$km km"
            }
        } ?: "? m"

        // ---- Time ----
        val timePart = travelTimeSeconds?.let {
            val totalMinutes = (it / 60.0).toInt().coerceAtLeast(1) // min = 1 phút
            if (totalMinutes >= 60) {
                val hours = totalMinutes / 60
                val minutes = totalMinutes % 60
                if (minutes > 0) {
                    "$hours h $minutes min"
                } else {
                    "$hours h"
                }
            } else {
                "$totalMinutes min"
            }
        } ?: "? min"

        return "Distance $distancePart • Time $timePart"
    }


    companion object {
        private const val ARG_ORDER_ID = "order_id"
        private const val ARG_ORDER_SEED_JSON = "order_seed_json"

        // Giữ hàm cũ (nếu nơi khác vẫn đang gọi theo id)
        fun newInstance(orderId: String) = NewOrderDialogFragment().apply {
            arguments = Bundle().apply { putString(ARG_ORDER_ID, orderId) }
        }

        // Hàm mới: truyền cả NearbyOrderPublic để có sẵn distance/time
        fun newInstanceWithSeed(order: NearbyOrderPublic) = NewOrderDialogFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_ORDER_ID, order.id)
                putString(ARG_ORDER_SEED_JSON, Gson().toJson(order))
            }
        }
    }
}
