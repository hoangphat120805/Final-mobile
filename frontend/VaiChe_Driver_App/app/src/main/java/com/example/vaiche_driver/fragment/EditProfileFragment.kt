package com.example.vaiche_driver.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.vaiche_driver.R
import com.example.vaiche_driver.databinding.FragmentEditProfileBinding
import com.example.vaiche_driver.viewmodel.Event
import com.example.vaiche_driver.viewmodel.SettingsViewModel
import com.example.vaiche_driver.viewmodel.SettingsViewModelFactory
import com.google.android.material.datepicker.MaterialDatePicker
import java.text.SimpleDateFormat
import java.util.*

class EditProfileFragment : Fragment() {

    private var _binding: FragmentEditProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels {
        SettingsViewModelFactory(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupGenderDropdown()
        setupDobPicker()
        setupSaveButton()
        observeVM()
    }

    private fun setupToolbar() {
        binding.toolbarEditProfile.setNavigationOnClickListener {
            parentFragmentManager.popBackStack() // quay về Settings
        }
    }

    private fun setupGenderDropdown() {
        val genders = listOf("Male", "Female", "Other")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, genders)
        binding.actvGender.setAdapter(adapter)
    }

    private fun setupDobPicker() {
        binding.tilDob.setEndIconOnClickListener { showDatePicker() }
        binding.etDob.setOnClickListener { showDatePicker() }
    }

    private fun showDatePicker() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select date of birth")
            .build()

        picker.addOnPositiveButtonClickListener { millis ->
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US) // ISO format
            val dateStr = sdf.format(Date(millis))
            binding.etDob.setText(dateStr)
        }

        picker.show(parentFragmentManager, "dob_picker")
    }

    private fun setupSaveButton() {
        binding.btnSaveProfile.setOnClickListener {
            val phone = binding.etPhone.text?.toString()
            val name = binding.etFullname.text?.toString()
            val email = binding.etEmail.text?.toString()
            val gender = binding.actvGender.text?.toString()
            val dob = binding.etDob.text?.toString()

            if (name.isNullOrBlank() || email.isNullOrBlank()) {
                Toast.makeText(requireContext(), "Name and email required", Toast.LENGTH_SHORT).show()
            } else {
                viewModel.updateProfile(name, gender, dob, email)
            }
        }
    }

    private fun observeVM() {
        viewModel.toastMessage.observe(viewLifecycleOwner) { ev: Event<String> ->
            ev.getContentIfNotHandled()?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.profileUpdated.observe(viewLifecycleOwner) { ev ->
            ev.getContentIfNotHandled()?.let {
                // Sau khi cập nhật thành công -> popBackStack về Settings
                parentFragmentManager.popBackStack()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
