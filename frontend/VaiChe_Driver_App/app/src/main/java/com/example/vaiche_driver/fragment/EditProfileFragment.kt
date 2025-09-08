package com.example.vaiche_driver.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.vaiche_driver.R
import com.example.vaiche_driver.viewmodel.SettingsViewModel
import com.example.vaiche_driver.viewmodel.SettingsViewModelFactory
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.*

class EditProfileFragment : Fragment() {

    private val viewModel: SettingsViewModel by viewModels({ requireParentFragment() }) {
        SettingsViewModelFactory(requireContext())
    }

    private var progress: ProgressBar? = null
    private lateinit var edtFullName: TextInputEditText
    private lateinit var edtEmail: TextInputEditText
    private lateinit var edtGender: AutoCompleteTextView
    private lateinit var edtBirthDate: TextInputEditText
    private lateinit var btnSave: MaterialButton

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_edit_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        progress = view.findViewById(R.id.progress_edit_profile)
        edtFullName = view.findViewById(R.id.edt_full_name)
        edtEmail = view.findViewById(R.id.edt_email)
        edtGender = view.findViewById(R.id.edt_gender)
        edtBirthDate = view.findViewById(R.id.edt_birth_date)
        btnSave = view.findViewById(R.id.btn_save_profile)

        edtBirthDate.setOnClickListener { showDatePicker() }

        btnSave.setOnClickListener {
            val fullName = edtFullName.text?.toString()?.takeIf { it.isNotBlank() }
            val email = edtEmail.text?.toString()?.takeIf { it.isNotBlank() }
            val gender = edtGender.text?.toString()?.takeIf { it.isNotBlank() }
            val birth = edtBirthDate.text?.toString()?.takeIf { it.isNotBlank() }

            if (fullName == null) {
                Toast.makeText(requireContext(), "Full name is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.updateProfile(fullName, gender, birth, email)
        }

        observeVM()
    }

    private fun showDatePicker() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select birth date")
            .build()
        picker.addOnPositiveButtonClickListener { millis ->
            val date = Date(millis)
            edtBirthDate.setText(isoFormat.format(date))
        }
        picker.show(parentFragmentManager, "birth_date_picker")
    }

    private fun observeVM() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            progress?.visibility = if (loading) View.VISIBLE else View.GONE
            btnSave.isEnabled = !loading
        }
        viewModel.toastMessage.observe(viewLifecycleOwner) { ev ->
            ev.getContentIfNotHandled()?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                if (it.contains("Profile updated", ignoreCase = true)) {
                    parentFragmentManager.popBackStack()
                }
            }
        }
    }
}
