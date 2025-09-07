package com.example.vaicheuserapp.ui.sell

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.vaicheuserapp.R
import com.example.vaicheuserapp.databinding.FragmentSetPlanBinding
import com.example.vaicheuserapp.ui.sell.LocationPickerDialog

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

        binding.cardPaymentSelection.setOnClickListener {
            showPaymentMethodSelectionDialog()
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
        val paymentMethods = arrayOf("Cash", "MoMo", "Bank Transfer")
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
        if (selectedAddress == null || selectedLatitude == null || selectedLongitude == null) {
            Toast.makeText(requireContext(), "Please select a pickup location.", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d("SetPlanFragment", "Scheduling pickup for: $selectedAddress, Payment: $selectedPaymentMethod")
        Toast.makeText(requireContext(), "Pickup scheduled (simulated)", Toast.LENGTH_SHORT).show()
        // TODO: In a real app, this would make an API call to create an order
        // For now, let's just navigate to the next screen or back to home
        findNavController().navigate(R.id.nav_home_fragment) // Example: Go back to home
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