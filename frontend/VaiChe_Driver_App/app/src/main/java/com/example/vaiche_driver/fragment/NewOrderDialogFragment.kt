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
import kotlin.math.roundToInt

class NewOrderDialogFragment : DialogFragment() {

    private val viewModel: NewOrderViewModel by viewModels()
    private val sharedViewModel: SharedViewModel by activityViewModels()

    private var orderId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.TransparentDialogTheme)
        arguments?.let { orderId = it.getString(ARG_ORDER_ID) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
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

        // Observe NearbyOrderPublic để bind vào UI “Nearby order”
        viewModel.order.observe(viewLifecycleOwner) { order ->
            if (order != null) {
                bindDataToViews(view, order)
                acceptButton.isEnabled = true
            } else {
                acceptButton.isEnabled = false
            }
        }

        // Load dữ liệu lần đầu (giữ nguyên tên hàm theo bạn dùng)
        if (savedInstanceState == null) {
            viewModel.loadOrderDetails(orderId)
        }

        // Accept → gọi API nhận đơn, bắn qua SharedViewModel, đóng dialog
        acceptButton.setOnClickListener {
            val id = orderId
            if (id == null) {
                Toast.makeText(context, "Missing order id", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.acceptOrder(id) { acceptedOrder ->   // acceptedOrder: OrderPublic
                // map OrderPublic -> Schedule (dùng rule localStatus bạn đã định nghĩa)
                val schedule = acceptedOrder.toSchedule(acceptedOrder.localStatus())
                sharedViewModel.onOrderAccepted(schedule)
                dismissAllowingStateLoss()
            }
        }

        // Close (Reject) → bắn rejected id qua SharedViewModel, đóng
        closeButton.setOnClickListener {
            val id = orderId
            if (id != null) {
                viewModel.rejectOrder(id) { rejectedOrderId ->
                    sharedViewModel.onOrderRejected(rejectedOrderId)
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
        // Dùng pickup_address cho cả name & address (đơn giản)
        startName.text = order.pickupAddress
        startAddress.text = order.pickupAddress

        // Điểm đến cố định theo yêu cầu
        endName.text = "VaiChe"
        endAddress.text = "Hồ Chí Minh"

        distanceTime.text = formatDistanceTime(order.distanceKm, order.travelTimeSeconds)
    }

    private fun formatDistanceTime(distanceKm: Double?, travelTimeSeconds: Double?): String {
        val distancePart = distanceKm?.let {
            val d = ((it * 10.0).roundToInt() / 10.0) // 1 decimal
            "~$d km"
        } ?: "~? km"

        val timePart = travelTimeSeconds?.let {
            val minutes = (it / 60.0).roundToInt()
            "~$minutes min"
        } ?: "~? min"

        return "Distance $distancePart • Time $timePart"
    }

    companion object {
        private const val ARG_ORDER_ID = "order_id"

        fun newInstance(orderId: String) = NewOrderDialogFragment().apply {
            arguments = Bundle().apply { putString(ARG_ORDER_ID, orderId) }
        }
    }
}
