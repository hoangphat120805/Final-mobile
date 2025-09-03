package com.example.vaiche_driver.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import com.example.vaiche_driver.R
import com.example.vaiche_driver.fragment.SuccessDialogFragment
import com.example.vaiche_driver.viewmodel.SharedViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class SetPlanDialogFragment : BottomSheetDialogFragment() {

    private val sharedViewModel: SharedViewModel by activityViewModels()
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Launcher để xin quyền truy cập vị trí
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                confirmPlanWithCurrentLocation()
            } else {
                Toast.makeText(context, "Location permission is required to set a plan.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Khởi tạo FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_set_plan, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val confirmButton = view.findViewById<Button>(R.id.btn_confirm)

        confirmButton.setOnClickListener {
            // Kiểm tra quyền trước khi lấy vị trí
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                confirmPlanWithCurrentLocation()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    @SuppressLint("MissingPermission") // Quyền đã được kiểm tra ở dòng gọi
    private fun confirmPlanWithCurrentLocation() {
        Toast.makeText(context, "Getting current location...", Toast.LENGTH_SHORT).show()

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (!isAdded) return@addOnSuccessListener // Đảm bảo Fragment còn tồn tại

            if (location != null) {
                // 1. Báo cáo vị trí và xác nhận kế hoạch
                sharedViewModel.onPlanConfirmed(location.latitude, location.longitude)

                // 2. Mở dialog "Waiting..."
                SuccessDialogFragment().show(parentFragmentManager, SuccessDialogFragment.TAG)

                // 3. Đóng dialog này lại
                dismiss()
            } else {
                Toast.makeText(context, "Could not get current location. Please ensure GPS is enabled.", Toast.LENGTH_LONG).show()
            }
        }.addOnFailureListener { e ->
            if (isAdded) {
                Toast.makeText(context, "Failed to get location: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}