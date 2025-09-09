package com.example.vaiche_driver.fragment

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import com.example.vaiche_driver.R
import com.example.vaiche_driver.viewmodel.SharedViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class SetPlanDialogFragment : BottomSheetDialogFragment() {

    private val TAG = "SetPlanDialog"

    private val sharedViewModel: SharedViewModel by activityViewModels()
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var btnConfirm: Button
    private var cbAck: CheckBox? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val fine = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
            val coarse = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            Log.w(TAG, "permission result -> fine=$fine, coarse=$coarse")
            if (fine || coarse) {
                confirmPlanWithCurrentLocation()
            } else if (isAdded) {
                Toast.makeText(
                    context,
                    "Location permission is required to set a plan.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        Log.w(TAG, "onCreate()")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.w(TAG, "onCreateView()")
        return inflater.inflate(R.layout.fragment_set_plan, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.w(TAG, "onViewCreated()")

        btnConfirm = view.findViewById(R.id.btn_confirm)
        cbAck = view.findViewById(R.id.cb_ack)

        btnConfirm.isEnabled = cbAck?.isChecked == true
        cbAck?.setOnCheckedChangeListener { _, checked ->
            Log.w(TAG, "cbAck checked=$checked")
            btnConfirm.isEnabled = checked
        }

        btnConfirm.setOnClickListener {
            Log.w(TAG, "Confirm clicked")

            if (cbAck?.isChecked != true) {
                Toast.makeText(context, "Please confirm before continuing.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (hasLocationPermission()) {
                confirmPlanWithCurrentLocation()
            } else {
                Log.w(TAG, "Requesting location permissions…")
                requestPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    private fun confirmPlanWithCurrentLocation() {
        if (!isAdded) return
        Log.w(TAG, "confirmPlanWithCurrentLocation(): START")
        Toast.makeText(context, "Fetching current location...", Toast.LENGTH_SHORT).show()

        val cts = CancellationTokenSource()
        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cts.token
        ).addOnSuccessListener { location ->
            if (!isAdded) return@addOnSuccessListener

            if (location != null) {
                val lat = location.latitude
                val lng = location.longitude
                Log.w(TAG, "getCurrentLocation OK: lat=$lat, lng=$lng")
                proceedWithLocation(lat, lng)
            } else {
                Log.w(TAG, "getCurrentLocation returned null -> fallback lastLocation")
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { last ->
                        if (!isAdded) return@addOnSuccessListener
                        if (last != null) {
                            val lat = last.latitude
                            val lng = last.longitude
                            Log.w(TAG, "lastLocation OK: lat=$lat, lng=$lng")
                            proceedWithLocation(lat, lng)
                        } else {
                            Log.e(TAG, "Both currentLocation and lastLocation are null")
                            Toast.makeText(
                                context,
                                "Unable to retrieve location. Please ensure GPS is enabled.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "lastLocation failure: ${e.message}", e)
                        Toast.makeText(
                            context,
                            "Failed to get location (lastLocation): ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "getCurrentLocation failure: ${e.message}", e)
            Toast.makeText(
                context,
                "Failed to get location: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun proceedWithLocation(lat: Double, lng: Double) {
        if (!isAdded) return
        Log.w(TAG, "onPlanConfirmed(lat=$lat, lng=$lng) -> notify ViewModel")
        sharedViewModel.onPlanConfirmed(lat, lng)

        Log.w(TAG, "Show SuccessDialogFragment")
        SuccessDialogFragment().show(parentFragmentManager, SuccessDialogFragment.TAG)

        Log.w(TAG, "dismiss() SetPlanDialog")
        dismiss()
    }
}
