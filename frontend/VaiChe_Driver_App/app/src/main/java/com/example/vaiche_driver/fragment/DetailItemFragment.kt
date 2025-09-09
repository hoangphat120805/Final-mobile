//package com.example.vaiche_driver.ui.item
//
//import android.app.AlertDialog
//import android.os.Bundle
//import android.view.View
//import android.widget.EditText
//import android.widget.ImageButton
//import android.widget.ImageView
//import android.widget.ProgressBar
//import android.widget.TextView
//import androidx.core.os.bundleOf
//import androidx.core.widget.addTextChangedListener
//import androidx.fragment.app.Fragment
//import androidx.fragment.app.viewModels
//import androidx.navigation.fragment.findNavController
//import com.bumptech.glide.Glide
//import com.example.vaiche_driver.R
//import dagger.hilt.android.AndroidEntryPoint
//import kotlin.math.max
//
//@AndroidEntryPoint
//class DetailItemFragment : Fragment(R.layout.fragment_detail_item) {
//
//    private val vm: DetailItemViewModel by viewModels()
//
//    private lateinit var tvTitle: TextView
//    private lateinit var tvDesc: TextView
//    private lateinit var tvUnit: TextView
//    private lateinit var tvEst: TextView
//    private lateinit var tvSubtotal: TextView
//    private lateinit var etQty: EditText
//    private lateinit var btnMinus: ImageButton
//    private lateinit var btnPlus: ImageButton
//    private lateinit var btnDelete: View
//    private lateinit var btnSubmit: View
//    private lateinit var progress: ProgressBar
//    private lateinit var ivBack: View
//    private lateinit var ivImage: ImageView
//
//    // Args (SafeArgs hoặc Bundle)
//    private val mode by lazy { DetailMode.valueOf(requireArguments().getString(ARG_MODE)!!) }
//    private val orderId by lazy { requireArguments().getString(ARG_ORDER_ID)!! }
//    private val categoryId by lazy { requireArguments().getString(ARG_CATEGORY_ID) } // ADD
//    private val categoryName by lazy { requireArguments().getString(ARG_CATEGORY_NAME) ?: "" }
//    private val unit by lazy { requireArguments().getString(ARG_UNIT) ?: "unit" }
//    private val estimatedPrice by lazy { requireArguments().getDouble(ARG_EST_PRICE, 0.0) }
//    private val orderItemId by lazy { requireArguments().getString(ARG_ORDER_ITEM_ID) } // EDIT
//    private val currentQty by lazy { requireArguments().getDouble(ARG_CURRENT_QTY, 0.0) }
//
//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        super.onViewCreated(view, savedInstanceState)
//
//        tvTitle = view.findViewById(R.id.tv_toolbar_title)
//        tvDesc = view.findViewById(R.id.tv_description)
//        tvUnit = view.findViewById(R.id.tv_unit_info)
//        tvEst = view.findViewById(R.id.tv_price_est)
//        tvSubtotal = view.findViewById(R.id.tv_subtotal)
//        etQty = view.findViewById(R.id.et_qty)
//        btnMinus = view.findViewById(R.id.btn_minus)
//        btnPlus = view.findViewById(R.id.btn_plus)
//        btnDelete = view.findViewById(R.id.btn_delete)
//        btnSubmit = view.findViewById(R.id.btn_submit)
//        progress = view.findViewById(R.id.progress)
//        ivBack = view.findViewById(R.id.iv_back_button)
//        ivImage = view.findViewById(R.id.iv_scrap_image_large)
//
//        tvTitle.text = categoryName
//        tvDesc.text = "" // nếu có mô tả của category thì set vào đây
//        tvUnit.text = "Unit: $unit"
//        tvEst.text = "Est. unit price: ${estimatedPrice.format()}"
//
//        // nếu có iconUrl bạn có thể truyền qua args và load ở đây
//        Glide.with(ivImage).load(R.drawable.ic_image_placeholder).into(ivImage)
//
//        if (mode == DetailMode.EDIT) {
//            btnDelete.visibility = View.VISIBLE
//            btnSubmit.isEnabled = true
//            (btnSubmit as? com.google.android.material.button.MaterialButton)?.text = getString(R.string.update)
//            etQty.setText(if (currentQty == 0.0) "" else currentQty.toString())
//        } else {
//            btnDelete.visibility = View.GONE
//            (btnSubmit as? com.google.android.material.button.MaterialButton)?.text = getString(R.string.add_to_order)
//            etQty.setText("")
//        }
//
//        ivBack.setOnClickListener { findNavController().navigateUp() }
//
//        btnMinus.setOnClickListener { adjustQty(-1.0) }
//        btnPlus.setOnClickListener { adjustQty(+1.0) }
//
//        etQty.addTextChangedListener { updateSubtotal() }
//
//        btnSubmit.setOnClickListener {
//            val qty = etQty.text.toString().toDoubleOrNull() ?: 0.0
//            if (qty <= 0.0) {
//                etQty.error = getString(R.string.invalid_quantity)
//                return@setOnClickListener
//            }
//            if (mode == DetailMode.ADD) {
//                vm.add(orderId, categoryId ?: return@setOnClickListener, qty)
//            } else {
//                vm.update(orderId, orderItemId ?: return@setOnClickListener, qty)
//            }
//        }
//
//        btnDelete.setOnClickListener {
//            AlertDialog.Builder(requireContext())
//                .setTitle(R.string.delete)
//                .setMessage(R.string.confirm_delete_item)
//                .setNegativeButton(android.R.string.cancel, null)
//                .setPositiveButton(R.string.delete) { _, _ ->
//                    vm.delete(orderId, orderItemId ?: return@setPositiveButton)
//                }.show()
//        }
//
//        viewLifecycleOwner.lifecycle.addObserver(FlowCollectorLifecycleOwner {
//            vm.ui.collect { s ->
//                progress.visibility = if (s.isLoading) View.VISIBLE else View.GONE
//                if (s.error != null) {
//                    // hiển thị nhanh bằng Toast
//                    requireContext().toast(s.error)
//                    vm.clearTransient()
//                }
//                if (s.success) {
//                    // báo cho OrderDetailFragment refresh (fragment result)
//                    parentFragmentManager.setFragmentResult(REQ_REFRESH_ORDER, bundleOf(KEY_REFRESH to true))
//                    findNavController().navigateUp()
//                }
//            }
//        })
//
//        updateSubtotal()
//    }
//
//    private fun adjustQty(delta: Double) {
//        val cur = etQty.text.toString().toDoubleOrNull() ?: 0.0
//        val next = max(0.0, cur + delta)
//        etQty.setText(if (next == 0.0) "" else next.noTrail())
//    }
//
//    private fun updateSubtotal() {
//        val qty = etQty.text.toString().toDoubleOrNull() ?: 0.0
//        val sub = qty * estimatedPrice
//        tvSubtotal.text = "Subtotal: ${sub.format()}"
//    }
//}
//
//private fun Double.noTrail(): String =
//    if (this % 1.0 == 0.0) String.format("%.0f", this) else String.format("%.2f", this)
//
//private fun Double.format(): String =
//    if (this % 1.0 == 0.0) String.format("%.0f", this) else String.format("%.2f", this)
//
//import android.content.Context
//import android.widget.Toast
//fun Context.toast(msg: String?) {
//    if (!msg.isNullOrBlank()) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
//}
