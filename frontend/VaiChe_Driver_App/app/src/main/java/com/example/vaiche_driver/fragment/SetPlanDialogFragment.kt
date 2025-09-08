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
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class SetPlanDialogFragment : BottomSheetDialogFragment() {

    private val TAG = "SetPlanDialog"

    private val sharedViewModel: SharedViewModel by activityViewModels()
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var btnConfirm: Button
    private var cbAck: CheckBox? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            Log.w(TAG, "requestPermissionLauncher result: isGranted=$isGranted")
            if (isGranted) {
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

            val hasFine = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            Log.w(TAG, "hasFine=$hasFine")

            if (hasFine) {
                confirmPlanWithCurrentLocation()
            } else {
                Log.w(TAG, "Requesting ACCESS_FINE_LOCATION permission…")
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun confirmPlanWithCurrentLocation() {
        if (!isAdded) return
        Log.w(TAG, "confirmPlanWithCurrentLocation(): START")
        Toast.makeText(context, "Getting current location...", Toast.LENGTH_SHORT).show()

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (!isAdded) return@addOnSuccessListener
                Log.w(TAG, "lastLocation success. location=$location")

                if (location != null) {
                    val lat = location.latitude
                    val lng = location.longitude
                    Log.w(TAG, "onPlanConfirmed(lat=$lat, lng=$lng) -> notify ViewModel")
                    // 1) Báo vị trí cho ViewModel (giả định hàm này sẽ set DriverState.FINDING_ORDER)
                    sharedViewModel.onPlanConfirmed(lat, lng)

                    // 2) Mở dialog trạng thái thành công/chờ tìm đơn
                    Log.w(TAG, "Show SuccessDialogFragment")
                    SuccessDialogFragment().show(parentFragmentManager, SuccessDialogFragment.TAG)

                    // 3) Đóng bottom sheet
                    Log.w(TAG, "dismiss() SetPlanDialog")
                    dismiss()
                } else {
                    Log.e(TAG, "lastLocation is NULL")
                    Toast.makeText(
                        context,
                        "Could not get current location. Please ensure GPS is enabled.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "lastLocation failure: ${e.message}", e)
                if (isAdded) {
                    Toast.makeText(
                        context,
                        "Failed to get location: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }
}
