package com.example.vaicheuserapp.ui.sell

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.vaicheuserapp.R
import com.example.vaicheuserapp.data.model.OrderCreate
import com.example.vaicheuserapp.data.network.RetrofitClient
import com.example.vaicheuserapp.databinding.FragmentSetPlanBinding
import com.example.vaicheuserapp.ui.sell.LocationPickerDialog
import kotlinx.coroutines.launch

// Implement the listener interface
class SetPlanFragment : Fragment(), LocationPickerDialog.OnLocationSelectedListener {

    private var _binding: FragmentSetPlanBinding? = null
    private val binding get() = _binding!!

    // State variables for selected location and payment
    private var selectedAddress: String? = null
    private var selectedLatitude: Double? = null
    private var selectedLongitude: Double? = null
    private var selectedPaymentMethod: String = "Cash" // Default to Cash

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSetPlanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupListeners()
        updateUI() // Initial UI update
    }

    private fun setupToolbar() {
        binding.ivBackButton.setOnClickListener {
            findNavController().navigateUp() // Navigate back using NavController
        }
        // The title "Sell" is hardcoded in XML for this screen
    }

    private fun setupListeners() {
        binding.btnSelectLocation.setOnClickListener {
            showLocationSelectionDialog() // Launches the Mapbox dialog
        }
        binding.btnViewAllPayments.setOnClickListener {
            showPaymentMethodSelectionDialog()
        }

        binding.btnSchedulePickup.setOnClickListener {
            attemptSchedulePickup()
        }
    }

    private fun updateUI() {
        binding.tvSelectedLocationAddress.text = selectedAddress ?: "Select pickup location"
        binding.tvSelectedPaymentMethod.text = selectedPaymentMethod
    }

    private fun showLocationSelectionDialog() {
        val dlg = LocationPickerDialog()
        dlg.show(childFragmentManager, "locPicker")
    }

    private fun showPaymentMethodSelectionDialog() {
        val paymentMethods = arrayOf("Cash", "Wallet")
        val checkedItem = paymentMethods.indexOf(selectedPaymentMethod)

        AlertDialog.Builder(requireContext())
            .setTitle("Select Payment Method")
            .setSingleChoiceItems(paymentMethods, checkedItem) { dialog, which ->
                selectedPaymentMethod = paymentMethods[which]
                updateUI()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }

    private fun attemptSchedulePickup() {
        if (selectedAddress == null) { // Only address is required by API
            Toast.makeText(requireContext(), "Please select a pickup location.", Toast.LENGTH_SHORT).show()
            return
        }

        // Disable button to prevent multiple clicks
        binding.btnSchedulePickup.isEnabled = false

        lifecycleScope.launch {
            try {
                // Construct the OrderCreate request
                val orderCreateRequest = OrderCreate(
                    pickupAddress = selectedAddress!!
                    // latitude and longitude are NOT sent in OrderCreate as per current API spec
                )

                val response = RetrofitClient.instance.createOrder(orderCreateRequest)
                if (response.isSuccessful && response.body() != null) {
                    val createdOrder = response.body()
                    Log.d("SetPlanFragment", "Order created: ${createdOrder?.id}")
                    Toast.makeText(requireContext(), "Order scheduled successfully! Order ID: ${createdOrder?.id}", Toast.LENGTH_LONG).show()
                    // Navigate back to home or a confirmation screen
                    findNavController().navigate(R.id.nav_home_fragment)
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("SetPlanFragment", "Failed to create order: ${response.code()} - ${errorBody}")
                    Toast.makeText(requireContext(), "Failed to schedule order: ${errorBody ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("SetPlanFragment", "Error scheduling order: ${e.message}", e)
                Toast.makeText(requireContext(), "Error scheduling order: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.btnSchedulePickup.isEnabled = true // Re-enable button
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onLocationSelected(
        address: String,
        latitude: Double,
        longitude: Double
    ) {
        this.selectedAddress = address
        this.selectedLatitude = latitude
        this.selectedLongitude = longitude
        updateUI() // Refresh the UI on SetPlanFragment
        Log.d("SetPlanFragment", "Location received: $address ($latitude, $longitude)")
    }
}