package com.example.vaiche_driver.fragment

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.vaiche_driver.R
import com.example.vaiche_driver.data.network.RetrofitClient
import com.example.vaiche_driver.data.repository.CategoryRepository
import com.example.vaiche_driver.data.repository.OrderRepository
import com.example.vaiche_driver.model.CategoryPublic
import com.example.vaiche_driver.model.OrderItemCreate
import com.example.vaiche_driver.model.OrderItemUpdate
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

class DetailItemFragment : Fragment() {

    // ---------------------- ARGS / MODES ----------------------
    companion object {
        private const val ARG_ORDER_ID = "order_id"
        private const val ARG_CATEGORY_ID = "category_id"
        private const val ARG_ORDER_ITEM_ID = "order_item_id"
        private const val ARG_INITIAL_QTY = "initial_qty"

        /** Dùng cho chế độ ADD */
        fun newInstance(orderId: String, categoryId: String) = DetailItemFragment().apply {
            arguments = bundleOf(
                ARG_ORDER_ID to orderId,
                ARG_CATEGORY_ID to categoryId
            )
        }

        /** Dùng cho chế độ EDIT (đã có orderItemId + số lượng hiện tại) */
        fun newInstanceForEdit(
            orderId: String,
            categoryId: String,
            orderItemId: String,
            initialQty: Double
        ) = DetailItemFragment().apply {
            arguments = bundleOf(
                ARG_ORDER_ID to orderId,
                ARG_CATEGORY_ID to categoryId,
                ARG_ORDER_ITEM_ID to orderItemId,
                ARG_INITIAL_QTY to initialQty
            )
        }
    }
    // ---------------------------------------------------------

    // Repo
    private val categoryRepo = CategoryRepository { RetrofitClient.instance }
    private val orderRepo = OrderRepository { RetrofitClient.instance }

    // Args
    private lateinit var orderId: String
    private lateinit var categoryId: String
    private var orderItemId: String? = null
    private var initialQty: Double? = null

    // UI state
    private var category: CategoryPublic? = null
    private var qty: Double = 0.0

    // Views
    private var tvTitle: TextView? = null
    private var ivImage: ImageView? = null
    private var tvDesc: TextView? = null
    private var tvUnit: TextView? = null
    private var etQty: EditText? = null
    private var btnMinus: ImageButton? = null
    private var btnPlus: ImageButton? = null
    private var tvPrice: TextView? = null
    private var tvSubtotal: TextView? = null
    private var btnSubmit: Button? = null
    private var progress: ProgressBar? = null
    private var backBtn: ImageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = requireArguments()
        orderId = args.getString(ARG_ORDER_ID).orEmpty()
        categoryId = args.getString(ARG_CATEGORY_ID).orEmpty()
        orderItemId = args.getString(ARG_ORDER_ITEM_ID)
        // Lưu ý: getDouble có default 0.0 nếu key không tồn tại – ta chỉ dùng nếu có orderItemId
        if (args.containsKey(ARG_INITIAL_QTY)) {
            initialQty = args.getDouble(ARG_INITIAL_QTY)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        // Đổi resource này cho khớp tên file layout chi tiết item của bạn
        return inflater.inflate(R.layout.fragment_detail_item, container, false)
    }

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        super.onViewCreated(v, savedInstanceState)
        bindViews(v)
        wireBack()
        wireQtyControls()
        loadCategoryAndBindUI()
        wireSubmit()
    }

    private fun bindViews(v: View) {
        backBtn = v.findViewById(R.id.iv_back_button)
        tvTitle = v.findViewById(R.id.tv_toolbar_title)
        ivImage = v.findViewById(R.id.iv_scrap_image_large)
        tvDesc = v.findViewById(R.id.tv_description)
        tvUnit = v.findViewById(R.id.tv_unit_info)
        etQty = v.findViewById(R.id.et_qty)
        btnMinus = v.findViewById(R.id.btn_minus)
        btnPlus = v.findViewById(R.id.btn_plus)
        tvPrice = v.findViewById(R.id.tv_price_est)
        tvSubtotal = v.findViewById(R.id.tv_subtotal)
        btnSubmit = v.findViewById(R.id.btn_submit)
        progress = v.findViewById(R.id.progress)
    }

    private fun wireBack() {
        backBtn?.setOnClickListener { parentFragmentManager.popBackStack() }
    }

    private fun wireQtyControls() {
        fun updateSubtotal() {
            val price = category?.estimatedPricePerUnit ?: 0.0
            tvSubtotal?.text = "Subtotal: ${(qty * price)}"
        }

        btnMinus?.setOnClickListener {
            qty = max(0.0, qty - 1.0)
            etQty?.setText(qty.toString())
            updateSubtotal()
        }
        btnPlus?.setOnClickListener {
            qty += 1.0
            etQty?.setText(qty.toString())
            updateSubtotal()
        }
        etQty?.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                qty = s?.toString()?.toDoubleOrNull() ?: 0.0
                updateSubtotal()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun loadCategoryAndBindUI() {
        progress?.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            val cat = withContext(Dispatchers.IO) {
                // Lấy list category rồi find theo id (backend chưa có endpoint detail)
                categoryRepo.getCategories().getOrNull()?.find { it.id == categoryId }
            }
            if (!isAdded) return@launch
            progress?.visibility = View.GONE

            if (cat == null) {
                Snackbar.make(requireView(), "Category not found", Snackbar.LENGTH_LONG).show()
                return@launch
            }
            category = cat

            // Bind UI
            tvTitle?.text = cat.name
            tvDesc?.text = cat.description ?: "No description"
            tvUnit?.text = "Unit: ${cat.unit}"
            tvPrice?.text = "Est. unit price: ${cat.estimatedPricePerUnit ?: 0.0}"
            if (!cat.iconUrl.isNullOrBlank()) {
                Glide.with(this@DetailItemFragment).load(cat.iconUrl).into(ivImage!!)
            }

            // Nếu là EDIT -> set sẵn qty & đổi nút
            val isEditMode = orderItemId != null
            if (isEditMode) {
                qty = (initialQty ?: 0.0).coerceAtLeast(0.0)
                etQty?.setText(qty.toString())
                btnSubmit?.text = "Update item"
            } else {
                btnSubmit?.text = "Add to order"
            }

            // Cập nhật subtotal ban đầu
            val price = cat.estimatedPricePerUnit ?: 0.0
            tvSubtotal?.text = "Subtotal: ${(qty * price)}"
        }
    }

    private fun wireSubmit() {
        btnSubmit?.setOnClickListener {
            val q = etQty?.text?.toString()?.toDoubleOrNull() ?: 0.0
            if (q <= 0.0) {
                Snackbar.make(requireView(), "Quantity must be > 0", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            progress?.visibility = View.VISIBLE
            viewLifecycleOwner.lifecycleScope.launch {
                val res = withContext(Dispatchers.IO) {
                    if (orderItemId != null) {
                        // EDIT
                        orderRepo.updateOrderItem(
                            orderId = orderId,
                            orderItemId = orderItemId!!,
                            itemUpdate = OrderItemUpdate(quantity = q)
                        )
                    } else {
                        // ADD
                        orderRepo.addItemsToOrder(
                            orderId = orderId,
                            items = OrderItemCreate(categoryId = categoryId, quantity = q)
                        )
                    }
                }
                if (!isAdded) return@launch
                progress?.visibility = View.GONE

                res.onSuccess {
                    Snackbar.make(requireView(),
                        if (orderItemId != null) "Item updated" else "Added to order",
                        Snackbar.LENGTH_SHORT
                    ).show()
                    parentFragmentManager.popBackStack()
                }.onFailure { e ->
                    Snackbar.make(requireView(), e.message ?: "Action failed", Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }
}
