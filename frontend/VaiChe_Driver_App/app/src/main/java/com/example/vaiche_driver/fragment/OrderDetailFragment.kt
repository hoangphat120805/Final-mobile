package com.example.vaiche_driver.fragment

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
import com.example.vaiche_driver.R
import com.example.vaiche_driver.adapter.BottomNavVisibilityManager
import com.example.vaiche_driver.model.OrderDetail
import com.example.vaiche_driver.model.OrderStatus
import com.example.vaiche_driver.viewmodel.OrderDetailViewModel
import com.google.android.material.appbar.MaterialToolbar
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

        viewModel.isLoading.observe(viewLifecycleOwner) { /* show/hide progress if you want */ }

        viewModel.errorMessage.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { msg ->
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.orderCompletedEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let {
                Toast.makeText(requireContext(), "Order completed!", Toast.LENGTH_SHORT).show()
                val id = orderId ?: return@let
                navigateToRatings(id)
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

        toolbar.title = "Order Detail #${order.id.takeLast(4)}"
        toolbar.setNavigationOnClickListener { activity?.onBackPressedDispatcher?.onBackPressed() }

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

        // --- Decide effective URLs (avoid duplicated pickup image as drop-off) ---
        val isDupPickupDrop = order.pickupPhotoUrl != null &&
                order.pickupPhotoUrl == order.dropoffPhotoUrl

        val effectiveDropoffUrl: String? = when {
            // If user has taken a local drop-off photo, prefer showing it (remoteUrl = null then)
            pendingDropoffUri != null -> null
            // If status is delivering and server returns the same URL for both -> treat drop-off as empty
            order.status == OrderStatus.delivering && isDupPickupDrop -> null
            else -> order.dropoffPhotoUrl
        }

        // Show photos (local first, then remote). If nothing -> placeholder to capture.
        setupPhotoView(
            photoView = pickupPhotoView,
            remoteUrl = order.pickupPhotoUrl,
            localUri = pendingPickupUri
        ) {
            photoTarget = PhotoTarget.PICKUP
            takeImage()
        }

        setupPhotoView(
            photoView = dropoffPhotoView,
            remoteUrl = effectiveDropoffUrl,
            localUri = pendingDropoffUri
        ) {
            photoTarget = PhotoTarget.DROPOFF
            takeImage()
        }

        when (order.status) {
            OrderStatus.scheduled -> {
                statusDot.background = ContextCompat.getDrawable(requireContext(), R.drawable.green_dot_background)
                statusText.text = "On the way to pickup"
                statusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.green_text_color))

                pickupSection.visibility = View.VISIBLE
                dropoffSection.visibility = View.GONE

                actionButton.text = "Pick-Up"
                actionButton.visibility = View.VISIBLE

                // Enable when we have an effective pickup (local or remote)
                val hasPickup = (pendingPickupUri != null) || (order.pickupPhotoUrl != null)
                actionButton.isEnabled = hasPickup

                actionButton.setOnClickListener {
                    val id = orderId ?: return@setOnClickListener
                    val pickupUri = pendingPickupUri
                    if (pickupUri == null && order.pickupPhotoUrl == null) {
                        Toast.makeText(requireContext(), "Please capture pickup photo first.", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    // Upload (pickup, pickup) to satisfy backend's 2-file requirement
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

                // Effective drop-off exists if we have local or (valid) remote URL
                val hasDropoffEffective = (pendingDropoffUri != null) || (effectiveDropoffUrl != null)
                actionButton.isEnabled = hasDropoffEffective

                actionButton.setOnClickListener {
                    val id = orderId ?: return@setOnClickListener
                    val pickupUri = pendingPickupUri
                    val dropUri = pendingDropoffUri
                    if (dropUri == null && effectiveDropoffUrl == null) {
                        Toast.makeText(requireContext(), "Please capture drop-off photo first.", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    // Upload both photos (pickup + dropoff) then complete
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

        // Render items
        itemsContainer.removeAllViews()
        val inflater = LayoutInflater.from(context)
        order.items.forEach { item ->
            val itemView = inflater.inflate(R.layout.include_order_item_row, itemsContainer, false)
            itemView.findViewById<TextView>(R.id.tv_item_name)
                .text = "${item.categoryName} (${formatCurrency(item.pricePerUnit)}/${item.categoryUnit})"
            itemView.findViewById<TextView>(R.id.tv_item_quantity)
                .text = "${item.quantity} ${item.categoryUnit}"
            itemView.findViewById<TextView>(R.id.tv_item_subtotal)
                .text = formatCurrency(item.quantity * item.pricePerUnit)
            itemsContainer.addView(itemView)
        }
    }

    // --- PHOTO helpers ---
    private fun takeImage() {
        lifecycleScope.launch {
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
        // Reload to re-bind UI (buttons enable state, etc.)
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

    private fun navigateToRatings(orderId: String) {
        val ratingsFragment = RatingsFragment.newInstance(orderId)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, ratingsFragment)
            .addToBackStack(null)
            .commit()
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
