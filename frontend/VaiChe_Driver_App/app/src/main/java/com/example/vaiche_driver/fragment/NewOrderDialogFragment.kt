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
import com.example.vaiche_driver.model.OrderDetail
import com.example.vaiche_driver.viewmodel.NewOrderViewModel
import com.example.vaiche_driver.viewmodel.SharedViewModel
import java.text.NumberFormat
import java.util.Locale

class NewOrderDialogFragment : DialogFragment() {

    // ViewModel riêng cho dialog này để quản lý dữ liệu chi tiết
    private val viewModel: NewOrderViewModel by viewModels()
    // ViewModel chung của toàn bộ ứng dụng để báo cáo kết quả
    private val sharedViewModel: SharedViewModel by activityViewModels()

    private var orderId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.TransparentDialogTheme) // Dùng style tùy chỉnh
        arguments?.let {
            orderId = it.getString(ARG_ORDER_ID)
        }
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

        // Lắng nghe dữ liệu đơn hàng từ ViewModel riêng
        viewModel.order.observe(viewLifecycleOwner) { order ->
            if (order != null) {
                bindDataToViews(view, order)
                acceptButton.isEnabled = true
            } else {
                Toast.makeText(context, "Order details could not be loaded.", Toast.LENGTH_SHORT).show()
                acceptButton.isEnabled = false
            }
        }

        // Tải dữ liệu lần đầu
        if (savedInstanceState == null) {
            viewModel.loadOrderDetails(orderId)
        }

        // Accept → báo cáo, mở MySchedule, và đóng dialog
        acceptButton.setOnClickListener {
            viewModel.acceptOrder { acceptedSchedule ->
                // 1. Báo cáo cho SharedViewModel để cập nhật trạng thái chung
                sharedViewModel.onOrderAccepted(acceptedSchedule)

                // 2. Đóng chính nó lại
                dismiss()
            }
        }

        // Close (Reject) → báo cáo cho SharedViewModel và đóng dialog
        closeButton.setOnClickListener {
            viewModel.rejectOrder { rejectedOrderId ->
                sharedViewModel.onOrderRejected(rejectedOrderId)
                dismiss()
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

    private fun bindDataToViews(view: View, order: OrderDetail) {
        val priceText = view.findViewById<TextView>(R.id.tv_price)
        val startName = view.findViewById<TextView>(R.id.tv_start_location_name)
        val startAddress = view.findViewById<TextView>(R.id.tv_start_location_address)
        val endName = view.findViewById<TextView>(R.id.tv_end_location_name)
        val endAddress = view.findViewById<TextView>(R.id.tv_end_location_address)
        val detailsText = view.findViewById<TextView>(R.id.tv_order_details)

        priceText.text = formatCurrency(order.totalAmount)
        startName.text = order.startLocationName
        startAddress.text = order.startLocationAddress
        endName.text = order.endLocationName
        endAddress.text = order.endLocationAddress
        detailsText.text = "Collect ~${order.totalWeight.toInt()}kg recyclable"
    }

    private fun formatCurrency(amount: Double): String {
        return try {
            val format = NumberFormat.getCurrencyInstance(Locale("vi", "VN"))
            format.format(amount).replace("₫", "đ")
        } catch (e: Exception) { "${amount.toLong()}đ" }
    }

    companion object {
        private const val ARG_ORDER_ID = "order_id"

        /**
         * "Cửa" để tạo dialog và truyền orderId vào một cách an toàn.
         */
        fun newInstance(orderId: String) = NewOrderDialogFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_ORDER_ID, orderId)
            }
        }
    }
}