package com.example.vaicheuserapp.ui.profile

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment // Import Fragment
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.vaicheuserapp.LoginActivity
import com.example.vaicheuserapp.R
import com.example.vaicheuserapp.data.model.UserPublic
import com.example.vaicheuserapp.data.model.UserUpdateRequest
import com.example.vaicheuserapp.data.network.RetrofitClient
import com.example.vaicheuserapp.databinding.FragmentProfileBinding // Change this to FragmentProfileBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ProfileFragment : Fragment() { // Extend Fragment

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private var currentUser: UserPublic? = null
    private var isEditing: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("ProfileFragment", "onViewCreated called.")

        // No window inset handling here for fragments, host activity does it.
        // Remove calls to ViewCompat.setOnApplyWindowInsetsListener

        setupListeners()
        // No bottom nav setup here, MainActivity handles it
        fetchUserProfile()
    }

    override fun onResume() {
        super.onResume()
        fetchUserProfile()
    }

    private fun setupListeners() {
        // Remove binding.ivBackButton.setOnClickListener { finish() }
        binding.btnEditProfile.setOnClickListener { toggleEditMode(true) }
        binding.btnSaveProfile.setOnClickListener { saveUserProfile() }
        binding.btnResetPassword.setOnClickListener { navigateToResetPassword() }
        binding.btnLogout.setOnClickListener { logoutUser() }

        binding.etBirthDateEdit.setOnClickListener { showDatePicker() }
        binding.etBirthDateEdit.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) showDatePicker()
        }
    }

    private fun fetchUserProfile() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getUserMe()
                if (response.isSuccessful && response.body() != null) {
                    currentUser = response.body()
                    populateProfileData(currentUser!!)
                    toggleEditMode(false)
                } else {
                    Log.e("ProfileFragment", "Failed to fetch user profile: ${response.errorBody()?.string()}")
                    Toast.makeText(requireContext(), "Failed to load profile", Toast.LENGTH_SHORT).show()
                    if (response.code() == 401) logoutUser()
                }
            } catch (e: Exception) {
                Log.e("ProfileFragment", "Error fetching user profile: ${e.message}", e)
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun populateProfileData(user: UserPublic) {
        binding.tvWelcomeName.text = "Welcome, ${user.fullName ?: user.phoneNumber}"
        binding.tvPhoneNumber.text = user.phoneNumber

        binding.tvFullNameView.text = user.fullName ?: "N/A"
        binding.etFullNameEdit.setText(user.fullName)

        binding.tvEmailView.text = user.email ?: "N/A"
        binding.etEmailEdit.setText(user.email)

        binding.tvGenderView.text = user.gender ?: "Unknown"
        binding.etGenderEdit.setText(user.gender)

        binding.tvBirthDateView.text = user.birthDate ?: "N/A"
        binding.etBirthDateEdit.setText(user.birthDate)

        binding.ivProfilePicture.load(user.id) { // Using ID as placeholder for now
            crossfade(true)
            placeholder(R.drawable.bg_image_placeholder)
            error(R.drawable.default_avatar)
        }
    }

    private fun toggleEditMode(enableEdit: Boolean) {
        isEditing = enableEdit
        val visibilityView = if (enableEdit) View.GONE else View.VISIBLE
        val visibilityEdit = if (enableEdit) View.VISIBLE else View.GONE

        binding.tvFullNameView.visibility = visibilityView
        binding.etFullNameEdit.visibility = visibilityEdit
        binding.etFullNameEdit.isEnabled = enableEdit

        binding.tvEmailView.visibility = visibilityView
        binding.etEmailEdit.visibility = visibilityEdit
        binding.etEmailEdit.isEnabled = enableEdit

        binding.tvGenderView.visibility = visibilityView
        binding.etGenderEdit.visibility = visibilityEdit
        binding.etGenderEdit.isEnabled = enableEdit

        binding.tvBirthDateView.visibility = visibilityView
        binding.etBirthDateEdit.visibility = visibilityEdit
        binding.etBirthDateEdit.isEnabled = enableEdit

        binding.btnEditProfile.visibility = visibilityView
        binding.btnResetPassword.visibility = visibilityView
        binding.btnLogout.visibility = visibilityView

        binding.btnSaveProfile.visibility = visibilityEdit
        binding.ivCameraIcon.visibility = visibilityEdit
    }

    private fun saveUserProfile() {
        val updatedFullName = binding.etFullNameEdit.text.toString().trim()
        val updatedEmail = binding.etEmailEdit.text.toString().trim()
        val updatedGender = binding.etGenderEdit.text.toString().trim()
        val updatedBirthDate = binding.etBirthDateEdit.text.toString().trim()
        val updatedAvtUrl = currentUser?.avtUrl

        val request = UserUpdateRequest(
            fullName = updatedFullName.ifEmpty { null },
            email = updatedEmail.ifEmpty { null },
            gender = updatedGender.ifEmpty { null },
            birthDate = updatedBirthDate.ifEmpty { null },
            phoneNumber = null,
            avtUrl = updatedAvtUrl
        )

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.updateUserMe(request)
                if (response.isSuccessful && response.body() != null) {
                    Toast.makeText(requireContext(), "Profile updated!", Toast.LENGTH_SHORT).show()
                    currentUser = response.body()
                    populateProfileData(currentUser!!)
                    toggleEditMode(false)
                } else {
                    Log.e("ProfileFragment", "Failed to update profile: ${response.errorBody()?.string()}")
                    Toast.makeText(requireContext(), "Update failed: ${response.errorBody()?.string()}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("ProfileFragment", "Error updating profile: ${e.message}", e)
                Toast.makeText(requireContext(), "An error occurred: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, selectedYear, selectedMonth, selectedDayOfMonth ->
                val selectedCalendar = Calendar.getInstance()
                selectedCalendar.set(selectedYear, selectedMonth, selectedDayOfMonth)
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                binding.etBirthDateEdit.setText(dateFormat.format(selectedCalendar.time))
            },
            year, month, day
        )
        datePickerDialog.show()
    }

    private fun navigateToResetPassword() {
        // This would be another Activity if it's outside the main bottom nav flow
        Toast.makeText(requireContext(), "Navigate to Reset Password screen", Toast.LENGTH_SHORT).show()
    }

    private fun logoutUser() {
        val sharedPreferences = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().remove("auth_token").apply()
        Toast.makeText(requireContext(), "Logged out!", Toast.LENGTH_SHORT).show()

        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish() // Finish the host activity
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}