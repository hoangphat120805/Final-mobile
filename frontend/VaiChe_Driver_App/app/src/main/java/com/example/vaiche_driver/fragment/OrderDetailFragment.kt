package com.example.vaiche_driver.fragment

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.vaiche_driver.MainActivity
import com.example.vaiche_driver.R
import com.example.vaiche_driver.adapter.BottomNavScreen
import com.example.vaiche_driver.adapter.BottomNavVisibilityManager
import com.example.vaiche_driver.data.network.RetrofitClient
import com.example.vaiche_driver.data.repository.OrderRepository
import com.example.vaiche_driver.model.OrderDetail
import com.example.vaiche_driver.model.OrderStatus
import com.example.vaiche_driver.fragment.ItemFragment
import com.example.vaiche_driver.viewmodel.OrderDetailViewModel
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat
import java.util.Locale

class OrderDetailFragment : Fragment() {

    private var orderId: String? = null
    private val viewModel: OrderDetailViewModel by viewModels()

    // --- CAMERA / PHOTO ---
    private var latestTmpUri: Uri? = null
    private var photoTarget: PhotoTarget? = null

    private val takeImageResult =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
            if (isSuccess) {
                latestTmpUri?.let { uri -> handlePhotoResult(uri) }
            }
        }

    enum class PhotoTarget { PICKUP, DROPOFF }
    // ----------------------

    private var visibilityManager: BottomNavVisibilityManager? = null
    private var pendingPickupUri: Uri? = null
    private var pendingDropoffUri: Uri? = null

    // Item actions
    private val orderRepo = OrderRepository { RetrofitClient.instance }
    private var selectedOrderItemId: String? = null
    private var selectedCategoryId: String? = null
    private var selectedQuantity: Double? = null
    private var selectedRowView: View? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is BottomNavVisibilityManager) {
            visibilityManager = context
            visibilityManager?.setBottomNavVisibility(false)
        }
    }

    override fun onDetach() {
        super.onDetach()
        visibilityManager?.setBottomNavVisibility(true)
        visibilityManager = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let { orderId = it.getString(ARG_ORDER_ID) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_order_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.orderDetail.observe(viewLifecycleOwner) { order ->
            order?.let { bindDataToViews(view, it) }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { msg ->
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.orderCompletedEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let {
                Toast.makeText(requireContext(), "Order completed!", Toast.LENGTH_SHORT).show()
                val id = orderId ?: return@let
                (activity as? MainActivity)?.selectMainTab(BottomNavScreen.SCHEDULE, clearBackStack = true)
            }
        }

        if (savedInstanceState == null) {
            viewModel.loadOrder(orderId)
        }
    }

    private fun bindDataToViews(view: View, order: OrderDetail) {
        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        val userAvatar = view.findViewById<ImageView>(R.id.iv_user_avatar)
        val userName = view.findViewById<TextView>(R.id.tv_user_name)
        val userPhone = view.findViewById<TextView>(R.id.tv_user_phone)
        val deliveryTime = view.findViewById<TextView>(R.id.tv_delivery_time)
        val startLocationName = view.findViewById<TextView>(R.id.tv_start_location_name_detail)
        val startLocationAddress = view.findViewById<TextView>(R.id.tv_start_location_address_detail)
        val endLocationName = view.findViewById<TextView>(R.id.tv_end_location_name_detail)
        val endLocationAddress = view.findViewById<TextView>(R.id.tv_end_location_address_detail)
        val itemsContainer = view.findViewById<LinearLayout>(R.id.container_order_items)
        val totalAmount = view.findViewById<TextView>(R.id.tv_total_amount)
        val totalWeight = view.findViewById<TextView>(R.id.tv_total_weight)
        val statusDot = view.findViewById<View>(R.id.status_dot)
        val statusText = view.findViewById<TextView>(R.id.tv_order_status)
        val actionButton = view.findViewById<Button>(R.id.btn_action)
        val pickupSection = view.findViewById<LinearLayout>(R.id.pickup_section)
        val dropoffSection = view.findViewById<LinearLayout>(R.id.dropoff_section)
        val pickupPhotoView = view.findViewById<View>(R.id.pickup_photo_view)
        val dropoffPhotoView = view.findViewById<View>(R.id.dropoff_photo_view)
        val ivMessage = view.findViewById<ImageView>(R.id.iv_message)

        val btnAdd = view.findViewById<ImageButton>(R.id.ib_add_item)
        val btnEdit = view.findViewById<ImageButton>(R.id.ib_edit_item)
        val btnDelete = view.findViewById<ImageButton>(R.id.ib_delete_item)

        toolbar.title = "Order Detail #${order.id.takeLast(4)}"
        toolbar.setNavigationOnClickListener { (activity as? MainActivity)?.selectMainTab(BottomNavScreen.SCHEDULE, clearBackStack = true) }
        ivMessage.setOnClickListener { navigateToMessages(order.id) }

        Glide.with(this).load(order.user.avatarUrl)
            .placeholder(R.drawable.ic_person_circle)
            .error(R.drawable.ic_person_circle)
            .circleCrop()
            .into(userAvatar)

        userName.text = order.user.fullName ?: "User"
        userPhone.text = order.user.phoneNumber
        deliveryTime.text = order.pickupTimestamp
        startLocationName.text = order.startLocationName
        startLocationAddress.text = order.startLocationAddress
        endLocationName.text = order.endLocationName
        endLocationAddress.text = order.endLocationAddress
        totalAmount.text = formatCurrency(order.totalAmount)
        totalWeight.text = "~${order.totalWeight.toInt()}kg"

        // Enable add/edit/delete only at scheduled/pending
        val enableItemActions = (order.status == OrderStatus.scheduled || order.status == OrderStatus.pending)
        btnAdd.isEnabled = enableItemActions
        btnEdit.isEnabled = enableItemActions
        btnDelete.isEnabled = enableItemActions
        btnAdd.alpha = if (enableItemActions) 1f else 0.4f
        btnEdit.alpha = if (enableItemActions) 1f else 0.4f
        btnDelete.alpha = if (enableItemActions) 1f else 0.4f

        if (enableItemActions) {
            btnAdd.setOnClickListener {
                val id = orderId ?: return@setOnClickListener
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, ItemFragment.newInstance(id))
                    .addToBackStack(null)
                    .commit()
            }
            btnEdit.setOnClickListener {
                val id = orderId ?: return@setOnClickListener
                val itemId = selectedOrderItemId
                val catId = selectedCategoryId
                val qty = selectedQuantity
                if (itemId == null || catId == null || qty == null) {
                    Toast.makeText(requireContext(), "Please tap an item below to edit", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                parentFragmentManager.beginTransaction()
                    .replace(
                        R.id.fragment_container,
                        DetailItemFragment.newInstanceForEdit(
                            orderId = id, categoryId = catId,
                            orderItemId = itemId, initialQty = qty
                        )
                    )
                    .addToBackStack(null)
                    .commit()
            }
            btnDelete.setOnClickListener {
                val id = orderId ?: return@setOnClickListener
                val itemId = selectedOrderItemId
                if (itemId == null) {
                    Toast.makeText(requireContext(), "Please tap an item below to delete", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete item")
                    .setMessage("Are you sure you want to delete this item from the order?")
                    .setNegativeButton("Abort", null)
                    .setPositiveButton("Delete") { _, _ ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            val res = withContext(Dispatchers.IO) { orderRepo.deleteOrderItem(id, itemId) }
                            res.onSuccess {
                                Toast.makeText(requireContext(), "Deleted item", Toast.LENGTH_SHORT).show()
                                clearSelection()
                                viewModel.loadOrder(orderId)
                            }.onFailure { e ->
                                Toast.makeText(requireContext(), e.message ?: "Delete error", Toast.LENGTH_LONG).show()
                            }
                        }
                    }.show()
            }
        } else {
            btnAdd.setOnClickListener(null)
            btnEdit.setOnClickListener(null)
            btnDelete.setOnClickListener(null)
        }

        // ---------------- PHOTO RENDERING ----------------
        val pickupUrl = order.pickupPhotoUrl
        val dropUrlFromServer = order.dropoffPhotoUrl

        // RULE: During Delivering, drop-off is ALWAYS blank by default,
        // unless the driver has just taken a local drop-off photo (pendingDropoffUri != null).
        // Only in Completed we show the server's drop-off image.
        val effectiveDropoffUrl: String? = when (order.status) {
            OrderStatus.completed -> dropUrlFromServer
            OrderStatus.delivering -> if (pendingDropoffUri != null) null else null // force placeholder
            else -> null // scheduled/pending: drop-off hidden/unused
        }

        // Pickup photo: show local (if any) else remote, else placeholder
        setupPhotoView(
            photoView = pickupPhotoView,
            remoteUrl = pickupUrl,
            localUri = pendingPickupUri
        ) {
            photoTarget = PhotoTarget.PICKUP
            takeImage()
        }

        // Drop-off photo:
        // - Delivering: placeholder unless localUri exists (pendingDropoffUri)
        // - Completed: show server or local (if just captured before complete)
        val dropLocal = pendingDropoffUri
        val dropRemote = effectiveDropoffUrl
        setupPhotoView(
            photoView = dropoffPhotoView,
            remoteUrl = dropRemote,
            localUri = dropLocal
        ) {
            photoTarget = PhotoTarget.DROPOFF
            takeImage()
        }
        // -------------------------------------------------

        // --- STATUS / ACTION ---
        when (order.status) {
            OrderStatus.scheduled -> {
                statusDot.background = ContextCompat.getDrawable(requireContext(), R.drawable.green_dot_background)
                statusText.text = "On the way to pickup"
                statusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.green_text_color))

                pickupSection.visibility = View.VISIBLE
                dropoffSection.visibility = View.GONE

                actionButton.text = "Pick-Up"
                actionButton.visibility = View.VISIBLE
                actionButton.isEnabled = (pendingPickupUri != null) || (pickupUrl != null)

                actionButton.setOnClickListener {
                    val id = orderId ?: return@setOnClickListener
                    val pickupUri = pendingPickupUri
                    if (pickupUri == null && pickupUrl == null) {
                        Toast.makeText(requireContext(), "Please capture pickup photo first.", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    viewModel.uploadPickupPhase(id, pickupUri)
                }
            }

            OrderStatus.delivering -> {
                statusDot.background = ContextCompat.getDrawable(requireContext(), R.drawable.orange_dot_background)
                statusText.text = "Delivering"
                statusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_delivering_text))

                pickupSection.visibility = View.VISIBLE
                dropoffSection.visibility = View.VISIBLE

                actionButton.text = "Complete Delivery"
                actionButton.visibility = View.VISIBLE

                // Because we force drop-off to be blank unless there is a local photo,
                // the button only enables when pendingDropoffUri exists.
                actionButton.isEnabled = (pendingDropoffUri != null)

                actionButton.setOnClickListener {
                    val id = orderId ?: return@setOnClickListener
                    val pickupUri = pendingPickupUri
                    val dropUri = pendingDropoffUri
                    if (dropUri == null) {
                        Toast.makeText(requireContext(), "Please capture drop-off photo first.", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    viewModel.uploadDropoffAndComplete(id, pickupUri, dropUri)
                }
            }

            OrderStatus.completed -> {
                statusDot.background = ContextCompat.getDrawable(requireContext(), R.drawable.blue_dot_background)
                statusText.text = "Done"
                statusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_done_text))

                pickupSection.visibility = View.VISIBLE
                dropoffSection.visibility = View.VISIBLE
                actionButton.visibility = View.GONE
            }

            else -> {
                pickupSection.visibility = View.GONE
                dropoffSection.visibility = View.GONE
                actionButton.visibility = View.GONE
            }
        }

        // --- Render items (with selection) ---
        itemsContainer.removeAllViews()
        val inflater = LayoutInflater.from(context)
        clearSelection()

        order.items.forEach { item ->
            val row = inflater.inflate(R.layout.include_order_item_row, itemsContainer, false)

            row.findViewById<TextView>(R.id.tv_item_name)
                .text = "${item.categoryName} (${formatCurrency(item.pricePerUnit)}/${item.categoryUnit})"
            row.findViewById<TextView>(R.id.tv_item_quantity)
                .text = "${item.quantity} ${item.categoryUnit}"
            row.findViewById<TextView>(R.id.tv_item_subtotal)
                .text = formatCurrency(item.quantity * item.pricePerUnit)

            row.setOnClickListener {
                selectedRowView?.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), android.R.color.transparent)
                )
                row.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.teal_200))

                selectedRowView = row
                selectedOrderItemId = item.id
                selectedCategoryId = item.categoryId
                selectedQuantity = item.quantity
            }

            itemsContainer.addView(row)
        }
    }

    private fun clearSelection() {
        selectedOrderItemId = null
        selectedCategoryId = null
        selectedQuantity = null
        selectedRowView?.setBackgroundColor(
            ContextCompat.getColor(requireContext(), android.R.color.transparent)
        )
        selectedRowView = null
    }

    // --- PHOTO helpers ---
    private fun takeImage() {
        viewLifecycleOwner.lifecycleScope.launch {
            latestTmpUri = getTmpFileUri()
            takeImageResult.launch(latestTmpUri)
        }
    }

    private suspend fun getTmpFileUri(): Uri = withContext(Dispatchers.IO) {
        val tmpFile = File.createTempFile("tmp_image_file", ".jpg", requireContext().cacheDir).apply {
            createNewFile()
            deleteOnExit()
        }
        FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.provider",
            tmpFile
        )
    }

    private fun handlePhotoResult(uri: Uri) {
        when (photoTarget) {
            PhotoTarget.PICKUP -> {
                pendingPickupUri = uri
                Toast.makeText(requireContext(), "Pickup photo captured", Toast.LENGTH_SHORT).show()
            }
            PhotoTarget.DROPOFF -> {
                pendingDropoffUri = uri
                Toast.makeText(requireContext(), "Drop-off photo captured", Toast.LENGTH_SHORT).show()
            }
            else -> Unit
        }
        orderId?.let { viewModel.loadOrder(it) }
    }

    private fun setupPhotoView(
        photoView: View,
        remoteUrl: String?,
        localUri: Uri?,
        onPlaceholderClick: (() -> Unit)?
    ) {
        val photoDisplay = photoView.findViewById<ImageView>(R.id.iv_photo_display)
        val placeholder = photoView.findViewById<LinearLayout>(R.id.container_photo_placeholder)

        when {
            localUri != null -> {
                placeholder.visibility = View.GONE
                photoDisplay.visibility = View.VISIBLE
                Glide.with(this).load(localUri).centerCrop().into(photoDisplay)
            }
            remoteUrl != null -> {
                placeholder.visibility = View.GONE
                photoDisplay.visibility = View.VISIBLE
                Glide.with(this).load(remoteUrl).centerCrop().into(photoDisplay)
            }
            else -> {
                placeholder.visibility = View.VISIBLE
                photoDisplay.visibility = View.GONE
                placeholder.setOnClickListener { onPlaceholderClick?.invoke() }
            }
        }
    }

    private fun navigateToMessages(conversationKey: String) {
        Toast.makeText(requireContext(), "Open messages (stub)", Toast.LENGTH_SHORT).show()
    }

    private fun formatCurrency(amount: Double): String {
        return try {
            val format = NumberFormat.getCurrencyInstance(Locale("vi", "VN"))
            format.format(amount).replace("₫", "đ")
        } catch (e: Exception) {
            "${amount.toLong()}đ"
        }
    }

    companion object {
        private const val ARG_ORDER_ID = "order_id"

        @JvmStatic
        fun newInstance(orderId: String) = OrderDetailFragment().apply {
            arguments = Bundle().apply { putString(ARG_ORDER_ID, orderId) }
        }
    }
}
