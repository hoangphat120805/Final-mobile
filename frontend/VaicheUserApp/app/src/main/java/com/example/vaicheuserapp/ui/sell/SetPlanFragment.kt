package com.example.vaicheuserapp.ui.sell

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController // <-- New import
import com.example.vaicheuserapp.R
import com.example.vaicheuserapp.databinding.FragmentSetPlanBinding

class SetPlanFragment : Fragment() {

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

        // Set up insets for the custom toolbar
        // The root layout of the fragment handles its own insets relative to its parent (NavHostFragment)
        // No need for complex ViewCompat.setOnApplyWindowInsetsListener here if MainActivity already sets WindowCompat.setDecorFitsSystemWindows(window, false)
        // and handles padding for the NavHostFragment.
        // For a simple toolbar like this, just ensuring it's constrained from the top is usually enough.

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
            showLocationSelectionDialog()
        }

        binding.cardPaymentSelection.setOnClickListener { // Clicking the whole card can also open it
            showPaymentMethodSelectionDialog()
        }
        binding.btnViewAllPayments.setOnClickListener { // Or clicking the "View all" button
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
        // TODO: This will become complex with Google Maps and Places API
        // For now, let's use a simple AlertDialog as a placeholder
        AlertDialog.Builder(requireContext())
            .setTitle("Select Location")
            .setMessage("Location selection feature coming soon!\n\n(Simulating selection for now)")
            .setPositiveButton("Set Sample Location") { dialog, _ ->
                selectedAddress = "123 Sample St, Sample City, Sample Province"
                selectedLatitude = 10.762622
                selectedLongitude = 106.660172
                updateUI()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }

    private fun showPaymentMethodSelectionDialog() {
        val paymentMethods = arrayOf("Cash", "Wallet")
        val checkedItem = paymentMethods.indexOf(selectedPaymentMethod) // Pre-select current method

        AlertDialog.Builder(requireContext())
            .setTitle("Select Payment Method")
            .setSingleChoiceItems(paymentMethods, checkedItem) { dialog, which ->
                selectedPaymentMethod = paymentMethods[which]
                updateUI()
                dialog.dismiss() // Dismiss after selection
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
}